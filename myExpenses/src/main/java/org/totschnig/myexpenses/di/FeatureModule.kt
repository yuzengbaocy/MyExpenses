package org.totschnig.myexpenses.di

import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.feature.FeatureManager
import org.totschnig.myexpenses.feature.OcrFeature
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.locale.UserLocaleProvider
import javax.inject.Singleton

@Module
class FeatureModule {
    private var ocrFeature: OcrFeature? = null

    @Provides
    fun provideOcrFeature(prefHandler: PrefHandler): OcrFeature? {
        if (ocrFeature != null) {
            return ocrFeature
        }
        return try {
            (Class.forName("org.totschnig.ocr.OcrFeatureImpl").getConstructor(PrefHandler::class.java)
                    .newInstance(prefHandler) as OcrFeature).also {
                ocrFeature = it
            }
        } catch (e: ClassNotFoundException) {
            CrashHandler.report(e)
            null
        }
    }

    @Provides
    @Singleton
    fun provideFeatureManager(localeProvider: UserLocaleProvider): FeatureManager = try {
        Class.forName("org.totschnig.myexpenses.util.locale.PlatformSplitManager")
                .getConstructor(UserLocaleProvider::class.java)
                .newInstance(localeProvider) as FeatureManager
    } catch (e: Exception) {
        object : FeatureManager() {}
    }
}