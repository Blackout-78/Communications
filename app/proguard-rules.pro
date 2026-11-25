# Room and Compose ship their own keep rules. Nothing extra needed yet.

# Shizuku starts the recorder by its class name inside its own process, so the names must stay.
-keep class com.blackout.communications.calls.ShellRecorder { *; }
-keep class com.blackout.communications.IRecorderService { *; }
-keep class com.blackout.communications.IRecorderService$Stub { *; }
