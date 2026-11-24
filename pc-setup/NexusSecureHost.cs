namespace PocketNexus
{
    using System;
    using System.Collections.Generic;
    using System.IO;
    using System.Net;
    using System.Net.Security;
    using System.Net.Sockets;
    using System.Security.Authentication;
    using System.Security.Cryptography;
    using System.Security.Cryptography.X509Certificates;
    using System.Text;
    using System.Text.RegularExpressions;
    using System.Threading;

    public sealed class NexusSecureHost
    {
        const string PairingVersion = "NEXUS1";
        const int ExchangeTimeoutMs = 8000;
        const int MaxDevices = 16;
        static readonly Regex DeviceName = new Regex(@"\A[A-Za-z0-9][A-Za-z0-9 _-]{0,31}\z");
        static readonly Regex Digest = new Regex(@"\A[0-9a-f]{64}\z");
        readonly object gate = new object();
        readonly SemaphoreSlim slots = new SemaphoreSlim(8);
        readonly TcpListener listener;
        readonly X509Certificate2 certificate;
        readonly string credentialFile;
        readonly Func<string> status;
        Dictionary<string, string> devices;
        bool running;

        public NexusSecureHost(X509Certificate2 certificate, string credentialFile,
                IPAddress address, int port, Func<string> status)
        {
            if (!certificate.HasPrivateKey || certificate.NotAfter <= DateTime.Now ||
                    certificate.NotBefore > DateTime.Now)
                throw new ArgumentException("A valid host certificate with a private key is required");
            this.certificate = certificate;
            this.credentialFile = credentialFile;
            this.status = status;
            Fingerprint = Hash(certificate.RawData);
            devices = ReadDevices();
            listener = new TcpListener(address, port);
            listener.Server.ExclusiveAddressUse = true;
            listener.Start(8);
            running = true;
            try
            {
                new Thread(Accept) { IsBackground = true }.Start();
            }
            catch
            {
                Stop();
                throw;
            }
        }

        public int Port { get { return ((IPEndPoint)listener.LocalEndpoint).Port; } }
        public string Fingerprint { get; private set; }

        public string[] DeviceNames()
        {
            lock (gate)
            {
                var names = new List<string>(devices.Keys);
                names.Sort(StringComparer.OrdinalIgnoreCase);
                return names.ToArray();
            }
        }

        public string Issue(string name)
        {
            if (!DeviceName.IsMatch(name)) throw new ArgumentException("Enter a device name of 1 to 32 letters, digits, spaces, hyphens or underscores");
            lock (gate)
            {
                if (!running) throw new InvalidOperationException("Secure status is disabled");
                if (devices.Count >= MaxDevices && !devices.ContainsKey(name))
                    throw new InvalidOperationException("Revoke a device before adding another");
                byte[] bytes = new byte[32];
                using (var rng = RandomNumberGenerator.Create()) rng.GetBytes(bytes);
                string key = Hex(bytes);
                var next = new Dictionary<string, string>(devices, devices.Comparer);
                next[name] = KeyDigest(key);
                Save(next);
                return PairingVersion + ":" + Port + ":" + Fingerprint + ":" + key;
            }
        }

        public bool Revoke(string name)
        {
            lock (gate)
            {
                var next = new Dictionary<string, string>(devices, devices.Comparer);
                if (!next.Remove(name)) return false;
                Save(next);
                return true;
            }
        }

        public void Stop()
        {
            lock (gate)
            {
                running = false;
                listener.Stop();
            }
        }

        Dictionary<string, string> ReadDevices()
        {
            var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            try
            {
                using (var input = new FileStream(credentialFile, FileMode.Open, FileAccess.Read, FileShare.Read))
                {
                    if (input.Length > 2048) throw new IOException("Invalid secure status device file");
                    using (var reader = new StreamReader(input, Encoding.ASCII))
                    {
                        string line;
                        while ((line = reader.ReadLine()) != null)
                        {
                            string[] fields = line.Split('\t');
                            if (fields.Length != 2 || !DeviceName.IsMatch(fields[0]) ||
                                    !Digest.IsMatch(fields[1]) || result.Count >= MaxDevices)
                                throw new IOException("Invalid secure status device file");
                            result.Add(fields[0], fields[1]);
                        }
                    }
                }
            }
            catch (FileNotFoundException) { }
            return result;
        }

        void Save(Dictionary<string, string> next)
        {
            string temporary = credentialFile + "." + Guid.NewGuid().ToString("N");
            try
            {
                var lines = new List<string>();
                foreach (var entry in next) lines.Add(entry.Key + "\t" + entry.Value);
                File.WriteAllLines(temporary, lines.ToArray(), Encoding.ASCII);
                if (File.Exists(credentialFile)) File.Replace(temporary, credentialFile, null);
                else File.Move(temporary, credentialFile);
                devices = next;
            }
            finally
            {
                if (File.Exists(temporary)) File.Delete(temporary);
            }
        }

        void Accept()
        {
            while (true)
            {
                TcpClient client;
                try { client = listener.AcceptTcpClient(); }
                catch (SocketException) { return; }
                catch (InvalidOperationException) { return; }
                if (!slots.Wait(0))
                {
                    client.Close();
                    continue;
                }
                if (!ThreadPool.QueueUserWorkItem(delegate
                {
                    try { Serve(client); }
                    finally { slots.Release(); }
                }))
                {
                    client.Close();
                    slots.Release();
                }
            }
        }

        void Serve(TcpClient client)
        {
            using (client)
            using (var deadline = new Timer(delegate
            {
                try { client.Close(); }
                catch (SocketException) { }
            }, null, ExchangeTimeoutMs, Timeout.Infinite))
            {
                try
                {
                    using (var tls = new SslStream(client.GetStream()))
                    {
                        tls.AuthenticateAsServer(certificate, false, SslProtocols.Tls12, false);
                        byte[] request = new byte[PairingVersion.Length + 1 + 64];
                        for (int i = 0; i < request.Length; i++)
                        {
                            int b = tls.ReadByte();
                            if (b < 32 || b > 126) return;
                            request[i] = (byte)b;
                        }
                        if (tls.ReadByte() != '\n') return;
                        string line = Encoding.ASCII.GetString(request);
                        if (!line.StartsWith(PairingVersion + " ", StringComparison.Ordinal)) return;
                        string key = line.Substring(PairingVersion.Length + 1);
                        if (!Digest.IsMatch(key)) return;
                        string response = Authorized(key) ? status() : "ERR auth";
                        byte[] reply = Encoding.ASCII.GetBytes(response + "\n");
                        tls.Write(reply, 0, reply.Length);
                        tls.Flush();
                    }
                }
                catch (IOException) { }
                catch (SocketException) { }
                catch (AuthenticationException) { }
                catch (ObjectDisposedException) { }
            }
        }

        bool Authorized(string key)
        {
            string digest = KeyDigest(key);
            lock (gate)
            {
                if (!running) return false;
                bool allowed = false;
                foreach (string expected in devices.Values)
                {
                    int diff = 0;
                    for (int i = 0; i < expected.Length; i++) diff |= expected[i] ^ digest[i];
                    allowed |= diff == 0;
                }
                return allowed;
            }
        }

        string KeyDigest(string key)
        {
            return Hash(Encoding.ASCII.GetBytes(Fingerprint + ":" + key));
        }

        static string Hash(byte[] bytes)
        {
            using (var hash = SHA256.Create()) return Hex(hash.ComputeHash(bytes));
        }

        static string Hex(byte[] bytes)
        {
            return BitConverter.ToString(bytes).Replace("-", "").ToLowerInvariant();
        }
    }
}
