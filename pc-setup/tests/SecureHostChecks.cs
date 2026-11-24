namespace PocketNexus
{
    using System;
    using System.IO;
    using System.Net;
    using System.Net.Security;
    using System.Net.Sockets;
    using System.Security.Authentication;
    using System.Security.Cryptography;
    using System.Security.Cryptography.X509Certificates;
    using System.Text;

    public static class SecureHostChecks
    {
        const string Status = "STATUS cpu=5 ram=10 up=20 bat=-1 ac=1";

        public static string[] Run(X509Certificate2 certificate, string credentialFile)
        {
            var results = new System.Collections.Generic.List<string>();
            var host = Start(certificate, credentialFile);
            string first, second, library;
            try
            {
                first = host.Issue("Retroid");
                second = host.Issue("Other handheld");
                library = host.Issue("Game catalog handheld", true);
                Check(library.StartsWith("NEXUS2:") &&
                    Exchange(host.Port, library, "NEXUSG " + library.Split(':')[3]) == "S\tFixture\tsteam\t1",
                    "Library-scoped key can read a display-only game list", results);
                Check(Exchange(host.Port, first, "NEXUSG " + first.Split(':')[3]) == "ERR auth",
                    "Existing status-only keys cannot read the game list", results);

                Check(first.Split(':')[3] != second.Split(':')[3], "Independent device keys", results);
                Check(Exchange(host.Port, first, null) == Status, "Pinned TLS handshake and authenticated status", results);
                Check(Exchange(host.Port, first, "NEXUS1 " + new string('0', 64)) == "ERR auth",
                    "Unknown key rejected even from loopback", results);
                Check(NoCommandReply(host.Port, first),
                    "Secure listener accepts only the status protocol", results);
                string wrongPin = first.Replace(host.Fingerprint, new string('0', 64));
                bool untrusted = false;
                try { Exchange(host.Port, wrongPin, null); }
                catch (AuthenticationException) { untrusted = true; }
                Check(untrusted, "Certificate mismatch rejected before application protocol", results);
                string saved = File.ReadAllText(credentialFile);
                Check(!saved.Contains(first.Split(':')[3]) && !saved.Contains(second.Split(':')[3]),
                    "Only credential digests written to disk", results);
                string replacement = host.Issue("Retroid");
                Check(Exchange(host.Port, first, null) == "ERR auth" &&
                    Exchange(host.Port, replacement, null) == Status, "Replacing one key invalidates its predecessor", results);
                first = replacement;
            }
            finally { host.Stop(); }

            host = Start(certificate, credentialFile);
            try
            {
                Check(Exchange(host.Port, first, null) == Status && Exchange(host.Port, second, null) == Status,
                    "Pinned identity and both device keys survive listener restart", results);
                Check(Exchange(host.Port, library, "NEXUSG " + library.Split(':')[3]) == "S\tFixture\tsteam\t1",
                    "Library scope persists across restart", results);
                Check(host.Revoke("retroid") && !host.Revoke("retroid"), "Device revocation is scoped and idempotent", results);
                Check(Exchange(host.Port, first, null) == "ERR auth" && Exchange(host.Port, second, null) == Status,
                    "Revocation preserves other devices", results);
            }
            finally { host.Stop(); }

            host = Start(certificate, credentialFile);
            try
            {
                Check(Exchange(host.Port, first, null) == "ERR auth" && Exchange(host.Port, second, null) == Status,
                    "Revocation persists across restart", results);
                Check(RejectsPlaintext(host.Port, second), "Plaintext receives no status", results);
            }
            finally { host.Stop(); }
            return results.ToArray();
        }

        static NexusSecureHost Start(X509Certificate2 certificate, string path)
        {
            return new NexusSecureHost(certificate, path, IPAddress.Loopback, 0, () => Status, stream =>
            {
                byte[] payload = Encoding.UTF8.GetBytes("S\tFixture\tsteam\t1\nEND 1\n");
                stream.Write(payload, 0, payload.Length);
            });
        }

        static void Check(bool condition, string name, System.Collections.Generic.List<string> results)
        {
            if (!condition) throw new InvalidOperationException(name);
            results.Add(name);
        }

        static string Exchange(int port, string pairing, string wireRequest)
        {
            string[] fields = pairing.Split(':');
            using (var client = new TcpClient("127.0.0.1", port))
            {
                client.ReceiveTimeout = 10000;
                client.SendTimeout = 10000;
                using (var tls = new SslStream(client.GetStream(), false, delegate(
                        object sender, X509Certificate remote, X509Chain chain, SslPolicyErrors errors)
                {
                    using (var hash = SHA256.Create())
                    {
                        string pin = BitConverter.ToString(hash.ComputeHash(remote.GetRawCertData()))
                            .Replace("-", "").ToLowerInvariant();
                        return pin == fields[2];
                    }
                }))
                {
                    tls.AuthenticateAsClient("Pocket Nexus Host Status", null, SslProtocols.Tls12, false);
                    byte[] bytes = Encoding.ASCII.GetBytes((wireRequest ?? ("NEXUS1 " + fields[3])) + "\n");
                    tls.Write(bytes, 0, bytes.Length);
                    tls.Flush();
                    using (var reader = new StreamReader(tls, Encoding.ASCII)) return reader.ReadLine();
                }
            }
        }

        static bool NoCommandReply(int port, string pairing)
        {
            try { return Exchange(port, pairing, "POWER shutdown") == null; }
            catch (IOException) { return true; }
        }

        static bool RejectsPlaintext(int port, string pairing)
        {
            using (var client = new TcpClient("127.0.0.1", port))
            {
                client.ReceiveTimeout = 10000;
                using (var stream = client.GetStream())
                {
                    byte[] bytes = Encoding.ASCII.GetBytes("NEXUS1 " + pairing.Split(':')[3] + "\n");
                    stream.Write(bytes, 0, bytes.Length);
                    try { return stream.ReadByte() != 'S'; }
                    catch (IOException) { return true; }
                }
            }
        }
    }
}
