namespace PocketNexus
{
    using System;
    using System.Globalization;
    using System.IO;
    using System.Net;
    using System.Net.Security;
    using System.Runtime.Serialization;
    using System.Runtime.Serialization.Json;
    using System.Security.Cryptography;
    using System.Security.Cryptography.X509Certificates;
    using System.Text;
    using System.Text.RegularExpressions;
    using System.Threading;

    public static class NexusVibepollo
    {
        const int TimeoutMs = 4000;
        const int MaxResponse = 65536;
        static readonly byte[] StoragePurpose = Encoding.ASCII.GetBytes("Pocket Nexus Vibepollo status v1");
        static readonly Regex PinPattern = new Regex(@"\A[0-9a-f]{64}\z");
        static readonly Regex TokenPattern = new Regex(@"\A[A-Za-z0-9+/._~=-]{16,160}\z");
        static readonly Regex VersionPattern = new Regex(@"\A[0-9A-Za-z.+_-]{1,64}\z");
        static readonly Regex DriverPattern = new Regex(@"\A[a-z_]{1,32}\z");

        [DataContract]
        sealed class Metadata
        {
            [DataMember(Name = "status")] public bool Status { get; set; }
            [DataMember(Name = "version")] public string Version { get; set; }
            [DataMember(Name = "encoder_status")] public Encoder Encoder { get; set; }
            [DataMember(Name = "virtual_display_driver")] public Driver Driver { get; set; }
        }

        [DataContract]
        sealed class Encoder
        {
            [DataMember(Name = "state")] public string State { get; set; }
            [DataMember(Name = "h264")] public bool? H264 { get; set; }
            [DataMember(Name = "hevc")] public bool? Hevc { get; set; }
            [DataMember(Name = "av1")] public bool? Av1 { get; set; }
        }

        [DataContract]
        sealed class Driver
        {
            [DataMember(Name = "status")] public string Status { get; set; }
        }

        public static string Fingerprint(string certificatePath)
        {
            if (new FileInfo(certificatePath).Length > MaxResponse)
                throw new InvalidOperationException("Select the host's public certificate file.");
            byte[] publicCertificate = File.ReadAllBytes(certificatePath);
            if (X509Certificate2.GetCertContentType(publicCertificate) != X509ContentType.Cert)
                throw new InvalidOperationException("Select the host's public certificate file.");
            using (var certificate = new X509Certificate2(publicCertificate))
                return Fingerprint(certificate);
        }

        public static string Configure(string settings, int port, string certificatePath, string token)
        {
            string fingerprint = Fingerprint(certificatePath);
            string summary = Check(port, fingerprint, token);
            byte[] encrypted = ProtectedData.Protect(
                Encoding.ASCII.GetBytes(token), StoragePurpose, DataProtectionScope.CurrentUser);
            string temporary = settings + "." + Guid.NewGuid().ToString("N");
            try
            {
                File.WriteAllLines(temporary, new[] { port.ToString(CultureInfo.InvariantCulture),
                    fingerprint, Convert.ToBase64String(encrypted) }, Encoding.ASCII);
                if (File.Exists(settings)) File.Replace(temporary, settings, null);
                else File.Move(temporary, settings);
            }
            finally
            {
                if (File.Exists(temporary)) File.Delete(temporary);
            }
            return summary;
        }

        public static string CheckSaved(string settings)
        {
            if (!File.Exists(settings)) return "Vibepollo status is not configured.";
            if (new FileInfo(settings).Length > 4096) throw new InvalidOperationException("Invalid saved settings.");
            string[] lines = File.ReadAllLines(settings, Encoding.ASCII);
            int port;
            if (lines.Length != 3 || !int.TryParse(lines[0], NumberStyles.None, CultureInfo.InvariantCulture, out port))
                throw new InvalidOperationException("Invalid saved settings.");
            byte[] secret = ProtectedData.Unprotect(
                Convert.FromBase64String(lines[2]), StoragePurpose, DataProtectionScope.CurrentUser);
            try { return Check(port, lines[1], Encoding.ASCII.GetString(secret)); }
            finally { Array.Clear(secret, 0, secret.Length); }
        }

        internal static string Check(int port, string pin, string token)
        {
            if (port < 1024 || port > 65535 || pin == null || !PinPattern.IsMatch(pin) ||
                    token == null || !TokenPattern.IsMatch(token))
                throw new InvalidOperationException("Check the management port, certificate and scoped API token.");

            var address = new Uri("https://127.0.0.1:" + port.ToString(CultureInfo.InvariantCulture) + "/api/metadata");
            var request = (HttpWebRequest)WebRequest.Create(address);
            request.Method = "GET";
            request.Proxy = null;
            request.AllowAutoRedirect = false;
            request.KeepAlive = false;
            request.Timeout = TimeoutMs;
            request.ReadWriteTimeout = TimeoutMs;
            request.MaximumResponseHeadersLength = 16;
            request.ConnectionGroupName = Guid.NewGuid().ToString("N");
            request.Headers[HttpRequestHeader.Authorization] = "Bearer " + token;
            request.ServerCertificateValidationCallback = delegate(object sender, X509Certificate remote,
                    X509Chain chain, SslPolicyErrors errors)
            {
                if (remote == null) return false;
                using (var certificate = new X509Certificate2(remote))
                {
                    DateTime now = DateTime.Now;
                    return now >= certificate.NotBefore && now <= certificate.NotAfter && Fingerprint(certificate) == pin;
                }
            };

            try
            {
                using (var deadline = new Timer(delegate { request.Abort(); }, null, TimeoutMs, Timeout.Infinite))
                using (var response = (HttpWebResponse)request.GetResponse())
                {
                    if (response.StatusCode != HttpStatusCode.OK)
                        throw new InvalidOperationException("The local management API did not return status.");
                    if (response.ContentLength > MaxResponse)
                        throw new InvalidOperationException("The local management response was too large.");
                    using (var body = response.GetResponseStream())
                    using (var payload = new MemoryStream())
                    {
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = body.Read(buffer, 0, buffer.Length)) != 0)
                        {
                            if (payload.Length + count > MaxResponse)
                                throw new InvalidOperationException("The local management response was too large.");
                            payload.Write(buffer, 0, count);
                        }
                        payload.Position = 0;
                        var metadata = (Metadata)new DataContractJsonSerializer(typeof(Metadata)).ReadObject(payload);
                        return Summarize(metadata);
                    }
                }
            }
            catch (WebException error)
            {
                using (var response = error.Response as HttpWebResponse)
                {
                    if (response != null && (response.StatusCode == HttpStatusCode.Forbidden ||
                            response.StatusCode == HttpStatusCode.Unauthorized))
                        throw new InvalidOperationException("The host rejected the token or its GET /api/metadata scope.");
                    if (error.Status == WebExceptionStatus.TrustFailure)
                        throw new InvalidOperationException("The local management certificate did not match or has expired.");
                    throw new InvalidOperationException("Could not read the local Vibepollo management API.");
                }
            }
            catch (SerializationException)
            {
                throw new InvalidOperationException("The local management API returned invalid metadata.");
            }
            finally
            {
                request.ServicePoint.CloseConnectionGroup(request.ConnectionGroupName);
            }
        }

        static string Summarize(Metadata metadata)
        {
            if (metadata == null || !metadata.Status)
                throw new InvalidOperationException("The local management API did not confirm status.");
            string version = metadata.Version != null && VersionPattern.IsMatch(metadata.Version)
                ? metadata.Version : "unknown";
            Encoder encoder = metadata.Encoder;
            string state = encoder != null && (encoder.State == "ready" || encoder.State == "failed")
                ? encoder.State : "unknown";
            string driver = metadata.Driver != null && metadata.Driver.Status != null &&
                DriverPattern.IsMatch(metadata.Driver.Status) ? metadata.Driver.Status.Replace('_', ' ') : "unknown";
            bool ready = state == "ready";
            return "Host version: " + version + Environment.NewLine +
                "Encoder: " + state + Environment.NewLine +
                "H.264: " + Capability(ready ? encoder.H264 : null) + Environment.NewLine +
                "HEVC: " + Capability(ready ? encoder.Hevc : null) + Environment.NewLine +
                "AV1: " + Capability(ready ? encoder.Av1 : null) + Environment.NewLine +
                "Virtual display driver: " + driver;
        }

        static string Capability(bool? available)
        {
            return !available.HasValue ? "unknown" : available.Value ? "available" : "unavailable";
        }

        static string Fingerprint(X509Certificate2 certificate)
        {
            using (var hash = SHA256.Create())
                return BitConverter.ToString(hash.ComputeHash(certificate.RawData))
                    .Replace("-", "").ToLowerInvariant();
        }
    }
}
