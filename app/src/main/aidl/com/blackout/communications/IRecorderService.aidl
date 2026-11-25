package com.blackout.communications;

import android.os.ParcelFileDescriptor;

interface IRecorderService {
    // Transaction id 16777114 is reserved by Shizuku for destroy().
    void destroy() = 16777114;
    // Fixed purpose only: records the call line into the file the app hands over. No generic shell.
    // Both return "" on success, or a short reason text on failure.
    String start(in ParcelFileDescriptor file) = 1;
    String stop() = 2;
}
