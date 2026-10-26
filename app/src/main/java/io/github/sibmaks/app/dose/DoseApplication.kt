package io.github.sibmaks.app.dose

import android.app.Application
import android.content.res.Configuration
import io.github.sibmaks.app.dose.widget.DoseWidget

class DoseApplication : Application() {
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DoseWidget.updateAll(this)
    }
}
