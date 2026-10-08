package com.vitngan.harness;

interface IShellService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    String run(in String cmd) = 1;
}
