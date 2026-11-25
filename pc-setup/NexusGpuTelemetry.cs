namespace PocketNexus
{
    using System;
    using System.Collections.Generic;
    using System.Diagnostics;
    using System.Globalization;
    using System.IO;
    using System.Text;
    using System.Text.RegularExpressions;
    using System.Threading;
    using System.Threading.Tasks;

    public static class NexusGpuTelemetry
    {
        const int MaxOutput = 16384;
        const string BaseFields = "index,name,utilization.gpu,memory.used,memory.total,temperature.gpu";
        static readonly object Gate = new object();
        static readonly Regex Row = new Regex(@"^\s*([0-9]{1,3}),\s*(.{1,120}?),([^,]+),([^,]+),([^,]+),([^,]+),([^,]+)$");
        static readonly Regex RowWithoutEncoder = new Regex(@"^\s*([0-9]{1,3}),\s*(.{1,120}?),([^,]+),([^,]+),([^,]+),([^,]+)$");
        static Task<string> current;

        public static Task<string> Sample()
        {
            lock (Gate)
            {
                if (current == null || current.IsCompleted)
                    current = Task.Factory.StartNew(Collect, CancellationToken.None,
                        TaskCreationOptions.None, TaskScheduler.Default);
                return current;
            }
        }

        static string Collect()
        {
            try
            {
                string command = Path.Combine(Environment.SystemDirectory, "nvidia-smi.exe");
                if (!File.Exists(command))
                {
                    string programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
                    if (String.IsNullOrEmpty(programFiles)) return "NVIDIA driver telemetry is unavailable.";
                    command = Path.Combine(programFiles, "NVIDIA Corporation", "NVSMI", "nvidia-smi.exe");
                    if (!File.Exists(command)) return "NVIDIA driver telemetry is unavailable.";
                }
                string samples = Query(command, BaseFields + ",utilization.encoder");
                bool includesEncoder = samples != null;
                if (!includesEncoder) samples = Query(command, BaseFields);
                return Format(samples, includesEncoder);
            }
            catch (TimeoutException) { return "The NVIDIA driver did not respond in time."; }
            catch (Exception) { return "NVIDIA driver telemetry is unavailable."; }
        }

        static string Query(string command, string fields)
        {
            var start = new ProcessStartInfo(command, "--query-gpu=" + fields + " --format=csv,noheader,nounits")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                WorkingDirectory = Path.GetDirectoryName(command)
            };
            using (Process process = Process.Start(start))
            {
                if (process == null) return null;
                Task<string> output = ReadBounded(process.StandardOutput);
                if (!process.WaitForExit(2400))
                {
                    try { process.Kill(); } catch (InvalidOperationException) { }
                    throw new TimeoutException();
                }
                if (!output.Wait(200)) throw new TimeoutException();
                return process.ExitCode == 0 ? output.Result : null;
            }
        }

        static async Task<string> ReadBounded(StreamReader reader)
        {
            using (var buffer = new StringWriter(CultureInfo.InvariantCulture))
            {
                var chunk = new char[512];
                for (int remaining = MaxOutput + 1; remaining > 0;)
                {
                    int count = await reader.ReadAsync(chunk, 0, Math.Min(chunk.Length, remaining)).ConfigureAwait(false);
                    if (count == 0) return buffer.ToString();
                    buffer.Write(chunk, 0, count);
                    remaining -= count;
                }
                return null;
            }
        }

        // This parser consumes only the fixed read-only query and omits serials and UUIDs.
        public static string Format(string output, bool includesEncoder)
        {
            if (String.IsNullOrEmpty(output) || output.Length > MaxOutput)
                return "No NVIDIA GPU readings available.";
            var report = new StringBuilder();
            var seen = new HashSet<int>();
            var pattern = includesEncoder ? Row : RowWithoutEncoder;
            using (var reader = new StringReader(output))
            {
                string line;
                while (seen.Count < 16 && (line = reader.ReadLine()) != null)
                {
                    if (line.Length > 512) continue;
                    Match row = pattern.Match(line);
                    if (!row.Success) continue;
                    string name = row.Groups[2].Value.Trim().Trim('"');
                    int index = Number(row.Groups[1].Value, 255).GetValueOrDefault(-1);
                    if (name.Length == 0 || Array.Exists(name.ToCharArray(), Char.IsControl)
                        || index < 0 || !seen.Add(index)) continue;
                    int? used = Number(row.Groups[4].Value, 1048576);
                    int? total = Number(row.Groups[5].Value, 1048576);
                    if (total == 0 || (used.HasValue && total.HasValue && used.Value > total.Value)) used = total = null;
                    if (report.Length > 0) report.AppendLine();
                    report.AppendFormat(CultureInfo.CurrentCulture, "GPU {0}   {1}", index, name).AppendLine();
                    report.Append("GPU utilization   ").AppendLine(Reading(Number(row.Groups[3].Value, 100), "%"));
                    report.Append("Video memory     ").AppendLine(Reading(used, " MiB") + " / " + Reading(total, " MiB"));
                    report.Append("Temperature      ").AppendLine(Reading(Number(row.Groups[6].Value, 150), " \u00b0C"));
                    report.Append("Video encoder    ").AppendLine(Reading(includesEncoder ? Number(row.Groups[7].Value, 100) : null, "%"));
                }
            }
            return report.Length == 0 ? "No NVIDIA GPU readings available." : report.ToString();
        }

        static int? Number(string value, int maximum)
        {
            int number;
            if (Int32.TryParse(value.Trim(), NumberStyles.None, CultureInfo.InvariantCulture, out number)
                    && number <= maximum) return number;
            return null;
        }

        static string Reading(int? value, string units)
        {
            return value.HasValue ? value.Value.ToString(CultureInfo.CurrentCulture) + units : "Unavailable";
        }
    }
}
