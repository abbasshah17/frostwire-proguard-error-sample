package com.frostwire.proguardtest;

public class TorrentInfoNotFound extends Exception {

    public TorrentInfoNotFound() {

    }

    public TorrentInfoNotFound(Exception ex) {
        super.initCause(ex);
    }
}
