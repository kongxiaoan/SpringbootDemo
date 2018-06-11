package com.kpa.springbootdemo;

import java.util.List;

public interface PermissionListener {
    void onGranted();

    void onDenied(List<String> deniedPermissions);
}
