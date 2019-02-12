package org.dhis2;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;

import com.crashlytics.android.Crashlytics;
import com.facebook.stetho.Stetho;
import com.google.android.gms.security.ProviderInstaller;

import org.dhis2.data.dagger.PerActivity;
import org.dhis2.data.dagger.PerServer;
import org.dhis2.data.dagger.PerUser;
import org.dhis2.data.database.DbModule;
import org.dhis2.data.forms.FormComponent;
import org.dhis2.data.forms.FormModule;
import org.dhis2.data.metadata.MetadataModule;
import org.dhis2.data.qr.QRModule;
import org.dhis2.data.schedulers.SchedulerModule;
import org.dhis2.data.schedulers.SchedulersProviderImpl;
import org.dhis2.data.server.ServerComponent;
import org.dhis2.data.server.ServerModule;
import org.dhis2.data.server.UserManager;
import org.dhis2.data.user.UserComponent;
import org.dhis2.data.user.UserModule;
import org.dhis2.usescases.login.LoginComponent;
import org.dhis2.usescases.login.LoginModule;
import org.dhis2.usescases.sync.SyncComponent;
import org.dhis2.usescases.sync.SyncModule;
import org.dhis2.utils.UtilsModule;
import org.dhis2.utils.timber.DebugTree;
import org.dhis2.utils.timber.ReleaseTree;
import org.hisp.dhis.android.core.configuration.ConfigurationManager;
import org.hisp.dhis.android.core.configuration.ConfigurationModel;

import javax.inject.Inject;
import javax.inject.Singleton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;
import io.fabric.sdk.android.Fabric;
import io.reactivex.Scheduler;
import io.reactivex.android.plugins.RxAndroidPlugins;
import io.reactivex.android.schedulers.AndroidSchedulers;
import timber.log.Timber;

/**
 * QUADRAM. Created by ppajuelo on 27/09/2017.
 */

public class App extends MultiDexApplication implements Components {
    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    private static final String DATABASE_NAME = "dhis.db";

    @Inject
    ConfigurationManager configurationManager;

    @NonNull
    @Singleton
    AppComponent appComponent;

    @Nullable
    @PerServer
    ServerComponent serverComponent;

    @Nullable
    @PerUser
    UserComponent userComponent;

    @Nullable
    @PerActivity
    FormComponent formComponent;

    @Nullable
    @PerActivity
    LoginComponent loginComponent;

    @Nullable
    @PerActivity
    SyncComponent syncComponent;

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(BuildConfig.DEBUG ? new DebugTree() : new ReleaseTree());
        Stetho.initializeWithDefaults(this);
        Fabric.with(this, new Crashlytics());

        setUpAppComponent();
        setUpServerComponent();
        setUpUserComponent();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            upgradeSecurityProvider();

        Scheduler asyncMainThreadScheduler = AndroidSchedulers.from(Looper.getMainLooper(), true);
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(schedulerCallable -> asyncMainThreadScheduler);

    }

    private void upgradeSecurityProvider() {
        try {
            ProviderInstaller.installIfNeededAsync(this, new ProviderInstaller.ProviderInstallListener() {
                @Override
                public void onProviderInstalled() {
                    Timber.e(App.class.getName(), "New security provider installed.");
                }

                @Override
                public void onProviderInstallFailed(int errorCode, Intent recoveryIntent) {
                    Timber.e(App.class.getName(), "New security provider install failed.");
                }
            });
        } catch (Exception ex) {
            Timber.e(App.class.getName(), "Unknown issue trying to install a new security provider", ex);
        }

    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    private void setUpAppComponent() {

        appComponent = prepareAppComponent().build();
        appComponent.inject(this);

    }

    private void setUpServerComponent() {
        ConfigurationModel configuration = configurationManager.get();
        if (configuration != null) {
            serverComponent = appComponent.plus(new ServerModule(configuration));
        }
    }


    private void setUpUserComponent() {
        UserManager userManager = serverComponent == null
                ? null : serverComponent.userManager();
        if (userManager != null && userManager.isUserLoggedIn().blockingFirst()) {
            userComponent = serverComponent.plus(new UserModule());
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // App component
    ////////////////////////////////////////////////////////////////////////
    @NonNull
    protected AppComponent.Builder prepareAppComponent() {
        return DaggerAppComponent.builder()
                .dbModule(new DbModule(DATABASE_NAME))
                .appModule(new AppModule(this))
                .schedulerModule(new SchedulerModule(new SchedulersProviderImpl()))
                .metadataModule(new MetadataModule())
                .qrModule(new QRModule())
                .utilModule(new UtilsModule());
    }

    @NonNull
    @Override
    public AppComponent appComponent() {
        return appComponent;
    }

    ////////////////////////////////////////////////////////////////////////
    // Login component
    ////////////////////////////////////////////////////////////////////////

    @NonNull
    @Override
    public LoginComponent createLoginComponent() {
        loginComponent = appComponent.plus(new LoginModule());
        return loginComponent;
    }

    @Nullable
    @Override
    public LoginComponent loginComponent() {
        return loginComponent;
    }

    @Override
    public void releaseLoginComponent() {
        loginComponent = null;
    }

    @NonNull
    @Override
    public SyncComponent createSyncComponent() {
        syncComponent = appComponent.plus(new SyncModule());
        return syncComponent;
    }

    @Nullable
    @Override
    public SyncComponent syncComponent() {
        return syncComponent;
    }

    @Override
    public void releaseSyncComponent() {
        syncComponent = null;
    }

    ////////////////////////////////////////////////////////////////////////
    // Server component
    ////////////////////////////////////////////////////////////////////////
    @NonNull
    @Override
    public ServerComponent createServerComponent(@NonNull ConfigurationModel configuration) {
        serverComponent = appComponent.plus(new ServerModule(configuration));
        return serverComponent;

    }

    @Nullable
    @Override
    public ServerComponent serverComponent() {
        return serverComponent;
    }

    @Override
    public void releaseServerComponent() {
        serverComponent = null;
    }

    @Nullable
    public ServerComponent getServerComponent() {
        return serverComponent;
    }

    ////////////////////////////////////////////////////////////////////////
    // User component
    ////////////////////////////////////////////////////////////////////////

    @NonNull
    @Override
    public UserComponent createUserComponent() {
        userComponent = serverComponent.plus(new UserModule());
        return userComponent;
    }

    @Override
    public UserComponent userComponent() {
        return userComponent;
    }

    @Override
    public void releaseUserComponent() {
        userComponent = null;
    }
    ////////////////////////////////////////////////////////////////////////
    // Form component
    ////////////////////////////////////////////////////////////////////////

    @NonNull
    public FormComponent createFormComponent(@NonNull FormModule formModule) {
        formComponent = userComponent.plus(formModule);
        return formComponent;
    }

    @Nullable
    public FormComponent formComponent() {
        return formComponent;
    }

    public void releaseFormComponent() {
        formComponent = null;
    }


    ////////////////////////////////////////////////////////////////////////
    // AndroidInjector
    ////////////////////////////////////////////////////////////////////////

    /**
     * Visible only for testing purposes.
     */
    public void setTestComponent(AppComponent testingComponent) {
        appComponent = testingComponent;
    }

}
