# Sentinel uses no reflection-based libraries that need keeping.
# Keep the entry points that the Android framework instantiates by name.
-keep class com.sentinel.adblock.MainActivity { *; }
-keep class com.sentinel.adblock.vpn.AdBlockVpnService { *; }
