package com.soundcloud.android.service.playback;

/** @noinspection UnusedDeclaration*/
public interface Errors {
    // include/media/stagefright/MediaErrors.h
    int STAGEFRIGHT_ERROR_IO = -1004;
    int STAGEFRIGHT_ERROR_CONNECTION_LOST = -1005;

    // external/opencore/pvmi/pvmf/include/pvmf_return_codes.h
    // Return code for general failure
    int OPENCORE_PVMFFailure = -1;
    // Error due to request timing out
    int OPENCORE_PVMFErrTimeout = -11;

    // Custom error for lack of MP error reporting on buffer run out
    int STAGEFRIGHT_ERROR_BUFFER_EMPTY = 99;
}
