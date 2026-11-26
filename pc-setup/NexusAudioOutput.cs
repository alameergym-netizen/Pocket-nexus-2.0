using System;
using System.Runtime.InteropServices;
using System.Threading.Tasks;

namespace PocketNexus
{
    public sealed class NexusAudioReading
    {
        public string Role { get; private set; }
        public bool HasEndpoint { get; private set; }
        public string Name { get; private set; }
        public int? Volume { get; private set; }
        public bool? Muted { get; private set; }

        internal NexusAudioReading(string role, bool present, string name, int? volume, bool? muted)
        {
            Role = role;
            HasEndpoint = present;
            Name = name;
            Volume = volume;
            Muted = muted;
        }
    }

    public static class NexusAudioOutput
    {
        private const int ERender = 0;
        private const int EMultimedia = 1;
        private const int ECommunications = 2;
        private const uint ClsCtxAll = 23;
        private const ushort VtLpWstr = 31;
        private static readonly Guid EndpointVolumeId = new Guid("5CDF2C82-841E-4546-9722-0CF74078229A");
        private static readonly object Sync = new object();
        private static Task<NexusAudioReading[]> pending;

        public static Task<NexusAudioReading[]> Sample()
        {
            // A reopened dialog can observe the existing query if Core Audio is still busy.
            lock (Sync)
            {
                if (pending == null || pending.IsCompleted)
                {
                    pending = Task.Run(new Func<NexusAudioReading[]>(Read));
                }
                return pending;
            }
        }

        private static NexusAudioReading[] Read()
        {
            IDeviceEnumerator enumerator = null;
            try
            {
                enumerator = (IDeviceEnumerator)new DeviceEnumerator();
                return new[] { ReadEndpoint(enumerator, EMultimedia, "Default media output"),
                    ReadEndpoint(enumerator, ECommunications, "Default communications output") };
            }
            catch (COMException)
            {
                return new[] { Missing("Default media output"), Missing("Default communications output") };
            }
            finally { Release(enumerator); }
        }

        private static NexusAudioReading ReadEndpoint(IDeviceEnumerator enumerator, int role, string label)
        {
            IDevice device = null;
            object volumeObject = null;
            try
            {
                if (enumerator.GetDefaultAudioEndpoint(ERender, role, out device) < 0 || device == null)
                {
                    return Missing(label);
                }
                string name = FriendlyName(device);
                int? volume = null;
                bool? muted = null;
                Guid volumeId = EndpointVolumeId;
                if (device.Activate(ref volumeId, ClsCtxAll, IntPtr.Zero, out volumeObject) >= 0)
                {
                    IEndpointVolume endpoint = volumeObject as IEndpointVolume;
                    if (endpoint != null)
                    {
                        float scalar;
                        if (endpoint.GetMasterVolumeLevelScalar(out scalar) >= 0 && scalar >= 0 && scalar <= 1)
                        {
                            volume = (int)Math.Round(scalar * 100, MidpointRounding.AwayFromZero);
                        }
                        bool isMuted;
                        if (endpoint.GetMute(out isMuted) >= 0) muted = isMuted;
                    }
                }
                return new NexusAudioReading(label, true, name, volume, muted);
            }
            catch (COMException) { return Missing(label); }
            finally
            {
                Release(volumeObject);
                Release(device);
            }
        }

        private static string FriendlyName(IDevice device)
        {
            IPropertyStore store = null;
            try
            {
                if (device.OpenPropertyStore(0, out store) < 0 || store == null) return null;
                PropertyKey nameKey = new PropertyKey
                {
                    Format = new Guid("A45C254E-DF1C-4EFD-8020-67D146A850E0"),
                    Property = 14
                };
                PropertyVariant value;
                int result = store.GetValue(ref nameKey, out value);
                try
                {
                    if (result < 0 || value.Type != VtLpWstr || value.Value == IntPtr.Zero) return null;
                    string name = Marshal.PtrToStringUni(value.Value).Trim();
                    return name.Length == 0 ? null : name;
                }
                finally { PropVariantClear(ref value); }
            }
            finally { Release(store); }
        }

        private static NexusAudioReading Missing(string label)
        {
            return new NexusAudioReading(label, false, null, null, null);
        }

        private static void Release(object value)
        {
            if (value != null && Marshal.IsComObject(value)) Marshal.ReleaseComObject(value);
        }

        [DllImport("ole32.dll")]
        private static extern int PropVariantClear(ref PropertyVariant value);

        [StructLayout(LayoutKind.Sequential)]
        private struct PropertyKey
        {
            internal Guid Format;
            internal uint Property;
        }

        [StructLayout(LayoutKind.Explicit, Size = 24)]
        private struct PropertyVariant
        {
            [FieldOffset(0)] internal ushort Type;
            [FieldOffset(8)] internal IntPtr Value;
        }

        [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
        private class DeviceEnumerator { }

        [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IDeviceEnumerator
        {
            [PreserveSig] int EnumAudioEndpoints(int flow, uint mask, out IntPtr devices);
            [PreserveSig] int GetDefaultAudioEndpoint(int flow, int role, out IDevice device);
        }

        [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IDevice
        {
            [PreserveSig] int Activate(ref Guid iid, uint context, IntPtr parameters,
                [MarshalAs(UnmanagedType.IUnknown)] out object activated);
            [PreserveSig] int OpenPropertyStore(uint access, out IPropertyStore properties);
        }

        [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IPropertyStore
        {
            [PreserveSig] int GetCount(out uint count);
            [PreserveSig] int GetAt(uint index, out PropertyKey key);
            [PreserveSig] int GetValue(ref PropertyKey key, out PropertyVariant value);
        }

        [ComImport, Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IEndpointVolume
        {
            // Preserve COM method order. Nexus only calls the two getters below.
            [PreserveSig] int RegisterControlChangeNotify(IntPtr notification);
            [PreserveSig] int UnregisterControlChangeNotify(IntPtr notification);
            [PreserveSig] int GetChannelCount(out uint count);
            [PreserveSig] int SetMasterVolumeLevel(float level, IntPtr context);
            [PreserveSig] int SetMasterVolumeLevelScalar(float level, IntPtr context);
            [PreserveSig] int GetMasterVolumeLevel(out float level);
            [PreserveSig] int GetMasterVolumeLevelScalar(out float level);
            [PreserveSig] int SetChannelVolumeLevel(uint channel, float level, IntPtr context);
            [PreserveSig] int SetChannelVolumeLevelScalar(uint channel, float level, IntPtr context);
            [PreserveSig] int GetChannelVolumeLevel(uint channel, out float level);
            [PreserveSig] int GetChannelVolumeLevelScalar(uint channel, out float level);
            [PreserveSig] int SetMute([MarshalAs(UnmanagedType.Bool)] bool muted, IntPtr context);
            [PreserveSig] int GetMute([MarshalAs(UnmanagedType.Bool)] out bool muted);
        }
    }
}
