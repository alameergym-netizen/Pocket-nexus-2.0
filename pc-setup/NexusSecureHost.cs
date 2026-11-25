namespace PocketNexus
{
    using System;
    using System.Collections.Generic;
    using System.Diagnostics;
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
        const string LibraryPairingVersion = "NEXUS2";
        const string LibraryCommand = "NEXUSG";
        const string LockCommand = "NEXUSL";
        const string DisplayCommand = "NEXUSD";
        const int LibraryTimeoutMs = 18000;
        const int ExchangeTimeoutMs = 8000;
        const int MaxDevices = 16;
        static readonly Regex DeviceName = new Regex(@"\A[A-Za-z0-9][A-Za-z0-9 _-]{0,31}\z");
        static readonly Regex Digest = new Regex(@"\A[0-9a-f]{64}\z");
        readonly object gate = new object();
        readonly SemaphoreSlim slots = new SemaphoreSlim(8);
        readonly TcpListener listener;
        readonly X509Certificate2 certificate;
        readonly string credentialFile;
        readonly string screenFile;
        readonly Func<string> status;
        readonly Action<Stream> library;
        readonly Func<string, string> screen;
        Dictionary<string, Credential> devices;
        HashSet<string> screenGrants;
        long lastScreenRequest;

        sealed class Credential
        {
            public readonly string Digest;
            public readonly bool Library;

            public Credential(string digest, bool library)
            {
                Digest = digest;
                Library = library;
            }
        }
        bool running;

        public NexusSecureHost(X509Certificate2 certificate, string credentialFile,
                IPAddress address, int port, Func<string> status)
            : this(certificate, credentialFile, address, port, status, null) { }

        public NexusSecureHost(X509Certificate2 certificate, string credentialFile,
                IPAddress address, int port, Func<string> status, Action<Stream> library)
            : this(certificate, credentialFile, address, port, status, library, null) { }

        public NexusSecureHost(X509Certificate2 certificate, string credentialFile,
                IPAddress address, int port, Func<string> status, Action<Stream> library, Func<string, string> screen)
        {
            if (!certificate.HasPrivateKey || certificate.NotAfter <= DateTime.Now ||
                    certificate.NotBefore > DateTime.Now)
                throw new ArgumentException("A valid host certificate with a private key is required");
            this.certificate = certificate;
            this.credentialFile = credentialFile;
            this.screenFile = credentialFile + ".screen";
            this.status = status;
            this.library = library;
            this.screen = screen;
            Fingerprint = Hash(certificate.RawData);
            devices = ReadDevices();
            screenGrants = ReadScreenGrants();
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

        public string Issue(string name) { return Issue(name, false); }

        public string Issue(string name, bool allowLibrary) { return Issue(name, allowLibrary, false); }

        public string Issue(string name, bool allowLibrary, bool allowScreen)
        {
            if (!DeviceName.IsMatch(name)) throw new ArgumentException("Enter a device name of 1 to 32 letters, digits, spaces, hyphens or underscores");
            lock (gate)
            {
                if (!running) throw new InvalidOperationException("Secure status is disabled");
                if (allowLibrary && library == null) throw new InvalidOperationException("PC library is unavailable");
                if (allowScreen && screen == null) throw new InvalidOperationException("Screen controls are unavailable");
                if (devices.Count >= MaxDevices && !devices.ContainsKey(name))
                    throw new InvalidOperationException("Revoke a device before adding another");
                byte[] bytes = new byte[32];
                using (var rng = RandomNumberGenerator.Create()) rng.GetBytes(bytes);
                string key = Hex(bytes);
                string digest = KeyDigest(key);
                var next = new Dictionary<string, Credential>(devices, devices.Comparer);
                next[name] = new Credential(digest, allowLibrary);
                if (allowScreen)
                {
                    // Write the additional grant first. It has no authority until its digest is
                    // in the active device file. An interrupted write keeps the previous key valid.
                    var grants = new HashSet<string>(StringComparer.Ordinal);
                    foreach (Credential existing in devices.Values)
                        if (screenGrants.Contains(existing.Digest)) grants.Add(existing.Digest);
                    grants.Add(digest);
                    WriteLines(screenFile, grants);
                    screenGrants = grants;
                }
                Save(next);
                string version = allowScreen ? (allowLibrary ? "NEXUS4" : "NEXUS3")
                    : (allowLibrary ? LibraryPairingVersion : PairingVersion);
                return version + ":" + Port + ":" + Fingerprint + ":" + key;
            }
        }

        public bool Revoke(string name)
        {
            lock (gate)
            {
                var next = new Dictionary<string, Credential>(devices, devices.Comparer);
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

        Dictionary<string, Credential> ReadDevices()
        {
            var result = new Dictionary<string, Credential>(StringComparer.OrdinalIgnoreCase);
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
                            bool validScope = fields.Length == 2 || (fields.Length == 3 &&
                                (fields[2] == "status" || fields[2] == "library"));
                            if (!validScope || !DeviceName.IsMatch(fields[0]) ||
                                    !Digest.IsMatch(fields[1]) || result.Count >= MaxDevices)
                                throw new IOException("Invalid secure status device file");
                            result.Add(fields[0], new Credential(fields[1], fields.Length == 3 && fields[2] == "library"));
                        }
                    }
                }
            }
            catch (FileNotFoundException) { }
            return result;
        }

        HashSet<string> ReadScreenGrants()
        {
            var grants = new HashSet<string>(StringComparer.Ordinal);
            try
            {
                using (var stream = new FileStream(screenFile, FileMode.Open, FileAccess.Read, FileShare.Read))
                using (var reader = new StreamReader(stream, Encoding.ASCII))
                {
                    if (stream.Length > 2048) throw new IOException("Invalid screen permissions file");
                    string line;
                    while ((line = reader.ReadLine()) != null)
                    {
                        if (!Digest.IsMatch(line) || grants.Count > MaxDevices || !grants.Add(line))
                            throw new IOException("Invalid screen permissions file");
                    }
                }
            }
            catch (IOException) { grants.Clear(); }
            catch (UnauthorizedAccessException) { grants.Clear(); }
            return grants;
        }

        void Save(Dictionary<string, Credential> next)
        {
            var lines = new List<string>();
            foreach (var entry in next)
                lines.Add(entry.Key + "\t" + entry.Value.Digest + "\t" + (entry.Value.Library ? "library" : "status"));
            WriteLines(credentialFile, lines);
            devices = next;
        }

        static void WriteLines(string file, IEnumerable<string> lines)
        {
            string temporary = file + "." + Guid.NewGuid().ToString("N");
            try
            {
                File.WriteAllLines(temporary, lines, Encoding.ASCII);
                if (File.Exists(file)) File.Replace(temporary, file, null);
                else File.Move(temporary, file);
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
                        if (line[PairingVersion.Length] != ' ') return;
                        string command = line.Substring(0, PairingVersion.Length);
                        bool games = command == LibraryCommand;
                        bool controls = command == LockCommand || command == DisplayCommand;
                        if (!games && !controls && command != PairingVersion) return;
                        string key = line.Substring(PairingVersion.Length + 1);
                        if (!Digest.IsMatch(key)) return;

                        if (controls)
                        {
                            WriteReply(tls, RunScreenControl(key, command == LockCommand ? "lock" : "displayoff"));
                            return;
                        }
                        bool authorized = Authorized(key, games, false);
                        if (authorized && games && library != null)
                        {
                            deadline.Change(LibraryTimeoutMs, Timeout.Infinite);
                            library(tls);
                            return;
                        }
                        string response = !authorized ? "ERR auth" : games ? "ERR library" : status();
                        WriteReply(tls, response);
                    }
                }
                catch (IOException) { }
                catch (SocketException) { }
                catch (AuthenticationException) { }
                catch (ObjectDisposedException) { }
            }
        }

        static void WriteReply(Stream tls, string response)
        {
            byte[] reply = Encoding.ASCII.GetBytes(response + "\n");
            tls.Write(reply, 0, reply.Length);
            tls.Flush();
        }

        string RunScreenControl(string key, string action)
        {
            lock (gate)
            {
                if (!Authorized(key, false, true)) return "ERR auth";
                if (screen == null) return "ERR screen";
                long now = Stopwatch.GetTimestamp();
                if (lastScreenRequest != 0 && now - lastScreenRequest < 2 * Stopwatch.Frequency)
                    return "ERR busy";
                try
                {
                    if (screen(action) != "OK") return "ERR screen";
                    lastScreenRequest = now;
                    return "OK";
                }
                catch (Exception) { return "ERR screen"; }
            }
        }

        bool Authorized(string key, bool requireLibrary, bool requireScreen)
        {
            string digest = KeyDigest(key);
            lock (gate)
            {
                if (!running) return false;
                bool allowed = false;
                foreach (Credential expected in devices.Values)
                {
                    int diff = 0;
                    for (int i = 0; i < expected.Digest.Length; i++) diff |= expected.Digest[i] ^ digest[i];
                    allowed |= diff == 0 && (!requireLibrary || expected.Library)
                        && (!requireScreen || screenGrants.Contains(expected.Digest));
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
