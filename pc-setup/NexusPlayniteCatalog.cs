namespace PocketNexus
{
    using System;
    using System.Collections.Generic;
    using System.Collections.ObjectModel;
    using System.Diagnostics;
    using System.Globalization;
    using System.IO;
    using System.Security.Cryptography;
    using System.Text;
    using System.Threading.Tasks;
    using Microsoft.Win32;

    // The opt-in export is metadata, never a command line or an executable path.
    public static class NexusPlayniteCatalog
    {
        public sealed class Entry
        {
            public readonly Guid Id;
            public readonly string Name;
            public readonly bool Installed;
            internal Entry(Guid id, string name, bool installed)
            { Id = id; Name = name; Installed = installed; }
        }

        public sealed class Snapshot
        {
            public readonly ReadOnlyCollection<Entry> Entries;
            public readonly string Sha256;
            internal Snapshot(List<Entry> entries, byte[] bytes)
            {
                Entries = entries.AsReadOnly();
                using (var hash = SHA256.Create())
                    Sha256 = BitConverter.ToString(hash.ComputeHash(bytes)).Replace("-", "").ToLowerInvariant();
            }
        }

        const int MaxBytes = 4 * 1024 * 1024;
        const int MaxEntries = 5000;

        public static Snapshot Read(string path)
        {
            byte[] bytes;
            using (var file = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete))
            {
                if (file.Length > MaxBytes) throw Invalid();
                using (var output = new MemoryStream())
                {
                    var buffer = new byte[8192];
                    int count;
                    while ((count = file.Read(buffer, 0, buffer.Length)) != 0)
                    {
                        if (output.Length + count > MaxBytes) throw Invalid();
                        output.Write(buffer, 0, count);
                    }
                    bytes = output.ToArray();
                }
            }
            string text = new UTF8Encoding(false, true).GetString(bytes);
            if (text.StartsWith("\uFEFF", StringComparison.Ordinal)) text = text.Substring(1);
            var entries = new List<Entry>();
            var seen = new HashSet<Guid>();
            using (var reader = new StringReader(text))
            {
                if (reader.ReadLine() != "NEXUS-PLAYNITE 1") throw Invalid();
                string row;
                while ((row = reader.ReadLine()) != null)
                {
                    if (row == "END " + entries.Count.ToString(CultureInfo.InvariantCulture))
                    {
                        if (reader.ReadLine() != null) throw Invalid();
                        return new Snapshot(entries, bytes);
                    }
                    if (entries.Count >= MaxEntries || row.Length > 239) throw Invalid();
                    string[] fields = row.Split('\t');
                    Guid id;
                    if (fields.Length != 3 || !Guid.TryParseExact(fields[0], "D", out id)
                        || id == Guid.Empty || !seen.Add(id) || (fields[1] != "0" && fields[1] != "1")
                        || fields[2].Length > 200 || String.IsNullOrWhiteSpace(fields[2])) throw Invalid();
                    foreach (char character in fields[2]) if (Char.IsControl(character)) throw Invalid();
                    entries.Add(new Entry(id, fields[2], fields[1] == "1"));
                }
            }
            throw Invalid();
        }

        static FormatException Invalid()
        { return new FormatException("The Playnite export is invalid or incomplete. Export visible games again in Playnite."); }

        // Re-read before dispatch: an old visible row cannot launch a different/new snapshot.
        public static string PrepareLaunchUri(string path, string expectedSha256, string gameId, string action)
        {
            Guid id;
            if ((action != "start" && action != "showgame") || !Guid.TryParseExact(gameId, "D", out id)
                || id == Guid.Empty) throw new ArgumentException("Select a game and a supported Playnite action.");
            Snapshot current = Read(path);
            if (!String.Equals(current.Sha256, expectedSha256, StringComparison.Ordinal))
                throw new InvalidOperationException("The Playnite export changed. Refresh the list and select the game again.");
            foreach (Entry entry in current.Entries)
                if (entry.Id == id)
                {
                    if (action == "start" && !entry.Installed)
                        throw new InvalidOperationException("This game was not installed in the export. Open its Playnite details instead.");
                    return "playnite://playnite/" + action + "/" + id.ToString("D");
                }
            throw new InvalidOperationException("The selected game is absent from the current export. Refresh the list.");
        }

        public static bool IsProtocolRegistered()
        {
            try
            {
                using (RegistryKey key = Registry.ClassesRoot.OpenSubKey(@"playnite\shell\open\command"))
                    return key != null && !String.IsNullOrWhiteSpace(key.GetValue(null) as string);
            }
            catch (Exception) { return false; }
        }

        public static Task<Snapshot> ReadAsync(string path)
        { return Task.Run(new Func<Snapshot>(delegate { return Read(path); })); }

        public static Task<string> OpenAsync(string path, string digest, string gameId, string action)
        {
            return Task.Run(new Func<string>(delegate {
                string uri = PrepareLaunchUri(path, digest, gameId, action);
                if (!IsProtocolRegistered()) throw new InvalidOperationException("Playnite's URI handler is unavailable. Open Playnite and enable its URI support, then retry.");
                using (Process process = Process.Start(new ProcessStartInfo(uri) { UseShellExecute = true })) { }
                return action == "start"
                    ? "Start request sent to Playnite. Check Playnite for game progress or errors."
                    : "Details request sent to Playnite.";
            }));
        }
    }
}
