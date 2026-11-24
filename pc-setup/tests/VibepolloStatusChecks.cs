namespace PocketNexus
{
    using System;
    using System.IO;
    using System.Net;
    using System.Net.Security;
    using System.Net.Sockets;
    using System.Security.Authentication;
    using System.Security.Cryptography.X509Certificates;
    using System.Text;
    using System.Threading;

    public static class VibepolloStatusChecks
    {
        const string Token = "synthetic-restricted-token-12345";
        const string Metadata = "{\"status\":true,\"version\":\"2.18.9\",\"encoder_status\":{" +
            "\"state\":\"ready\",\"h264\":true,\"hevc\":true,\"av1\":false}," +
            "\"virtual_display_driver\":{\"status\":\"version_incompatible\"}}";

        public static string[] Run(X509Certificate2 certificate, string directory)
        {
            var checks = new System.Collections.Generic.List<string>();
            string certFile = Path.Combine(directory, "public.cer");
            string settings = Path.Combine(directory, "vibepollo.txt");
            File.WriteAllBytes(certFile, certificate.Export(X509ContentType.Cert));
            string pin = NexusVibepollo.Fingerprint(certFile);
            bool bearerObserved = false;
            using (var server = new Endpoint(certificate, request =>
            {
                bearerObserved = request.StartsWith("GET /api/metadata HTTP/1.1", StringComparison.Ordinal) &&
                    request.IndexOf("Authorization: Bearer " + Token, StringComparison.OrdinalIgnoreCase) >= 0;
                return Response("200 OK", Metadata);
            }))
            {
                string summary = NexusVibepollo.Configure(settings, server.Port, certFile, Token);
                Check(bearerObserved && summary.Contains("Host version: 2.18.9") &&
                    summary.Contains("HEVC: available") && summary.Contains("AV1: unavailable") &&
                    summary.Contains("Virtual display driver: version incompatible"),
                    "Scoped metadata request and encoder/driver summary", checks);
                Check(!File.ReadAllText(settings).Contains(Token) &&
                    NexusVibepollo.CheckSaved(settings) == summary,
                    "Current User credential protection and saved refresh", checks);
                Check(Fails(() => NexusVibepollo.Check(server.Port, new string('0', 64), Token)),
                    "Certificate mismatch fails on the same port after a valid check", checks);
            }
            using (var server = new Endpoint(certificate, request => Response("401 Unauthorized", "")))
            {
                Check(Fails(() => NexusVibepollo.Configure(settings, server.Port, certFile, Token)),
                    "Unauthorized credentials do not replace saved settings", checks);
                Check(File.Exists(settings) && !File.ReadAllText(settings).StartsWith(server.Port + "\r\n"),
                    "Successful configuration remains after rejected credentials", checks);
            }
            using (var server = new Endpoint(certificate, request =>
                Response("200 OK", "{\"status\":true,\"version\":\"2.0.0\"}")))
            {
                string summary = NexusVibepollo.Check(server.Port, pin, Token);
                Check(summary.Contains("H.264: unknown") && summary.Contains("Virtual display driver: unknown"),
                    "Older hosts cannot imply unavailable or confirmed capabilities", checks);
            }
            using (var server = new Endpoint(certificate, request => Response("200 OK", new string('x', 65537))))
            {
                Check(Fails(() => NexusVibepollo.Check(server.Port, pin, Token)),
                    "Oversized response rejected", checks);
            }
            var redirectTarget = new TcpListener(IPAddress.Loopback, 0);
            redirectTarget.Start();
            try
            {
                int port = ((IPEndPoint)redirectTarget.LocalEndpoint).Port;
                using (var server = new Endpoint(certificate, request =>
                    Response("302 Found", "", "Location: https://127.0.0.1:" + port + "/redirect\r\n")))
                    Check(Fails(() => NexusVibepollo.Check(server.Port, pin, Token)) && !redirectTarget.Pending(),
                        "Redirect does not forward an authenticated request", checks);
            }
            finally { redirectTarget.Stop(); }
            return checks.ToArray();
        }

        static bool Fails(Action action)
        {
            try { action(); return false; }
            catch (InvalidOperationException) { return true; }
        }

        static void Check(bool result, string name, System.Collections.Generic.List<string> checks)
        {
            if (!result) throw new InvalidOperationException(name);
            checks.Add(name);
        }

        static string Response(string code, string body, string extraHeaders = "")
        {
            return "HTTP/1.1 " + code + "\r\nConnection: close\r\nContent-Type: application/json\r\n" +
                "Content-Length: " + Encoding.UTF8.GetByteCount(body) + "\r\n" + extraHeaders + "\r\n" + body;
        }

        sealed class Endpoint : IDisposable
        {
            readonly TcpListener listener = new TcpListener(IPAddress.Loopback, 0);
            readonly Thread worker;

            public Endpoint(X509Certificate2 certificate, Func<string, string> reply)
            {
                listener.Start();
                worker = new Thread(() =>
                {
                    while (true)
                    {
                        TcpClient client;
                        try { client = listener.AcceptTcpClient(); }
                        catch (SocketException) { return; }
                        catch (InvalidOperationException) { return; }
                        using (client)
                        {
                            try
                            {
                                client.ReceiveTimeout = 4500;
                                client.SendTimeout = 4500;
                                using (var tls = new SslStream(client.GetStream()))
                                {
                                    tls.AuthenticateAsServer(certificate, false, SslProtocols.Tls12, false);
                                    using (var input = new StreamReader(tls, Encoding.ASCII))
                                    {
                                        var headers = new StringBuilder();
                                        string line;
                                        while (!string.IsNullOrEmpty(line = input.ReadLine())) headers.AppendLine(line);
                                        if (line == null) continue;
                                        byte[] bytes = Encoding.UTF8.GetBytes(reply(headers.ToString()));
                                        tls.Write(bytes, 0, bytes.Length);
                                        tls.Flush();
                                    }
                                }
                            }
                            catch (IOException) { }
                            catch (AuthenticationException) { }
                        }
                    }
                });
                worker.IsBackground = true;
                worker.Start();
            }

            public int Port { get { return ((IPEndPoint)listener.LocalEndpoint).Port; } }

            public void Dispose()
            {
                listener.Stop();
                worker.Join(5000);
            }
        }
    }
}
