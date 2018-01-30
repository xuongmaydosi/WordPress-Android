package org.wordpress.android.ui.accounts.signup;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.R;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.ui.accounts.signup.SiteCreationService.OnSiteCreationStateUpdated;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.AutoForeground.ServiceEventConnection;

public class SiteCreationCreatingFragment extends SiteCreationBaseFormFragment<SiteCreationListener> {
    public static final String TAG = "site_creating_fragment_tag";

    private static final String ARG_SITE_TITLE = "ARG_SITE_TITLE";
    private static final String ARG_SITE_TAGLINE = "ARG_SITE_TAGLINE";
    private static final String ARG_SITE_SLUG = "ARG_SITE_SLUG";
    private static final String ARG_SITE_THEME_ID = "ARG_SITE_THEME_ID";

    private static final String KEY_IN_MODAL_MODE = "KEY_IN_MODAL_MODE";
    private static final String KEY_CREATION_FINISHED = "KEY_CREATION_FINISHED";

    private ServiceEventConnection mServiceEventConnection;

    private ImageView mImageView;
    private View mProgressContainer;
    private View mErrorContainer;
    private TextView[] mLabels;

    private boolean mInModalMode;
    private boolean mCreationFinished;

    public boolean isInModalMode() {
        return mInModalMode;
    }

    public static SiteCreationCreatingFragment newInstance(String siteTitle, String siteTagline, String siteSlug,
            String themeId) {
        SiteCreationCreatingFragment fragment = new SiteCreationCreatingFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SITE_TITLE, siteTitle);
        args.putString(ARG_SITE_TAGLINE, siteTagline);
        args.putString(ARG_SITE_SLUG, siteSlug);
        args.putString(ARG_SITE_THEME_ID, themeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected @LayoutRes int getContentLayout() {
        return R.layout.site_creation_creating_screen;
    }

    @Override
    protected void setupContent(ViewGroup rootView) {
        mImageView = rootView.findViewById(R.id.image);
        mProgressContainer = rootView.findViewById(R.id.progress_container);
        mErrorContainer = rootView.findViewById(R.id.error_container);

        // construct an array with the labels in reverse order
        mLabels = new TextView[] {
                rootView.findViewById(R.id.site_creation_creating_preparing_frontend),
                rootView.findViewById(R.id.site_creation_creating_configuring_theme),
                rootView.findViewById(R.id.site_creation_creating_configuring_content),
                rootView.findViewById(R.id.site_creation_creating_fetching_info),
                rootView.findViewById(R.id.site_creation_creating_laying_foundation)};
    }

    @Override
    protected void onHelp() {
        if (mSiteCreationListener != null) {
            mSiteCreationListener.helpSiteCreatingScreen();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            String siteTitle = getArguments().getString(ARG_SITE_TITLE);
            String siteTagline = getArguments().getString(ARG_SITE_TAGLINE);
            String siteSlug = getArguments().getString(ARG_SITE_SLUG);
            String themeId = getArguments().getString(ARG_SITE_THEME_ID);

            // on first appearance start the Service to perform the site creation
            mInModalMode = true;
            SiteCreationService.createSite(getContext(), siteTitle, siteTagline, siteSlug, themeId);
        } else {
            mInModalMode = savedInstanceState.getBoolean(KEY_IN_MODAL_MODE, false);
            mCreationFinished = savedInstanceState.getBoolean(KEY_CREATION_FINISHED, false);
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        showHomeButton(!mInModalMode);

        if (savedInstanceState == null) {
            AnalyticsTracker.track(AnalyticsTracker.Stat.LOGIN_MAGIC_LINK_OPEN_EMAIL_CLIENT_VIEWED);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mCreationFinished) {
            // connect to the Service. We'll receive updates via EventBus.
            mServiceEventConnection = new ServiceEventConnection(getContext(), SiteCreationService.class, this);
        } else {
            disableUntil(R.id.site_creation_creating_preparing_frontend);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mServiceEventConnection != null) {
            // disconnect from the Service
            mServiceEventConnection.disconnect(getContext(), this);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IN_MODAL_MODE, mInModalMode);
        outState.putBoolean(KEY_CREATION_FINISHED, mCreationFinished);
    }

    private void disableUntil(@IdRes int textViewId) {
        boolean enabled = false;

        // traverse the array (elements are in "reverse" order already) and disable them until the provided on is reach.
        //  From that point on, enable the labels found
        for(TextView tv : mLabels) {
            if (tv.getId() == textViewId) {
                enabled = true;
            }

            tv.setEnabled(enabled);
        }
    }

    private void setModalMode(boolean inModalMode) {
        mInModalMode = inModalMode;
        showHomeButton(!mInModalMode);
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onSiteCreationPhaseUpdated(OnSiteCreationStateUpdated event) {
        AppLog.i(T.NUX, "Received state: " + event.getState().name());

        mProgressContainer.setVisibility(View.VISIBLE);
        mErrorContainer.setVisibility(View.GONE);

        switch (event.getState()) {
            case IDLE:
                disableUntil(0);
                break;
            case NEW_SITE:
                disableUntil(R.id.site_creation_creating_laying_foundation);
                break;
            case FETCHING_NEW_SITE:
                disableUntil(R.id.site_creation_creating_fetching_info);
                break;
            case SET_TAGLINE:
                disableUntil(R.id.site_creation_creating_configuring_content);
                break;
            case SET_THEME:
                disableUntil(R.id.site_creation_creating_configuring_theme);
                break;
            case FAILURE:
                setModalMode(false);
                mImageView.setImageResource(R.drawable.img_site_error_camera_pencils_226dp);
                mProgressContainer.setVisibility(View.GONE);
                mErrorContainer.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                disableUntil(R.id.site_creation_creating_preparing_frontend);
                mSiteCreationListener.creationSuccess();
                break;
        }
    }
}
