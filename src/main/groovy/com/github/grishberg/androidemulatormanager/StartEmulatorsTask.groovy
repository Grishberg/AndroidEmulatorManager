package com.github.grishberg.androidemulatormanager

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by grishberg on 19.11.17.
 */
class StartEmulatorsTask extends DefaultTask {
    AndroidEmulatorManager emulatorManager
    EmulatorManagerConfig extConfig

    @TaskAction
    void runTask() {
        emulatorManager.startEmulators(extConfig.emulatorArgs)
    }
}
