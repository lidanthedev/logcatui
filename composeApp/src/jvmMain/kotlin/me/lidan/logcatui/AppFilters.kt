package me.lidan.logcatui

internal fun filterDevices(
    devices: List<DeviceDescriptor>,
    query: String,
): List<DeviceDescriptor> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) {
        return devices
    }

    return devices.filter { device ->
        device.serial.lowercase().contains(needle) ||
            device.model.lowercase().contains(needle) ||
            device.state.lowercase().contains(needle)
    }
}

internal fun filterProcesses(
    processes: List<ProcessDescriptor>,
    query: String,
): List<ProcessDescriptor> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) {
        return processes
    }

    return processes.filter { process ->
        process.name.lowercase().contains(needle) ||
            process.pid.toString().contains(needle)
    }
}
