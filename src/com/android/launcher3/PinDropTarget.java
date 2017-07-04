package com.android.launcher3;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.Toast;

import com.android.launcher3.util.Thunk;

public class PinDropTarget extends ButtonDropTarget {

    private static final String TAG = "PinDropTarget";

    private static TeraApiService mTeraApiService;
    private Context mContext;
    private Handler mUIHandler;
    @Thunk Launcher mLauncher;
    private static ProgressDialog mProgressDialog;
    private static Pair<String, Boolean> mCachedState;

    public PinDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PinDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mLauncher =  (Launcher) context;
        mUIHandler = new Handler(mLauncher.getMainLooper());
        mProgressDialog = new ProgressDialog(context);
        mProgressDialog.setMessage(mContext.getString(R.string.pin_app_in_progress));
        mTeraApiService = TeraApiService.getInstance(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.pin_target_hover_tint);
        setDrawable(R.drawable.ic_pin_launcher);
    }

    @Override
    protected boolean supportsDrop(DragSource source, ItemInfo info) {
        return supportsDrop(getContext(), info);
    }

    public static boolean supportsDrop(Context context, Object info) {
        Pair<ComponentName, Integer> componentInfo = getAppInfoFlags(info);
        if (componentInfo == null)
            return false;
        if ((componentInfo.second & AppInfo.DOWNLOADED_FLAG) == 0)
            return false;
        String packageName = componentInfo.first.getPackageName();
        boolean pinned = mTeraApiService.isAppPinned(packageName);
        mCachedState = Pair.create(packageName, pinned);
        return mTeraApiService.isAllowPinUnpinApps() && !pinned;
    }

    /**
     * @return the component name and flags if {@param info} is an AppInfo or an app shortcut.
     */
    private static Pair<ComponentName, Integer> getAppInfoFlags(Object item) {
        if (item instanceof AppInfo) {
            AppInfo info = (AppInfo) item;
            return Pair.create(info.componentName, info.flags);
        } else if (item instanceof ShortcutInfo) {
            ShortcutInfo info = (ShortcutInfo) item;
            ComponentName component = info.getTargetComponent();
            if (info.itemType == LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION
                    && component != null) {
                return Pair.create(component, info.flags);
            } else if (info.itemType == LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT
                    && component != null) {
                return Pair.create(component, AppInfo.DOWNLOADED_FLAG);
            }
        }
        return null;
    }

    @Override
    void completeDrop(final DragObject d) {
        Object info = d.dragInfo;
        final Pair<ComponentName, Integer> componentInfo = getAppInfoFlags(info);
        final Runnable doPinAction = new Runnable() {
            @Override
            public void run() {
                String packageName = componentInfo.first.getPackageName();
                boolean isSuccess = mTeraApiService.pinApp(packageName);
                mProgressDialog.dismiss();
                if (!isSuccess) {
                    mUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String msg = mContext.getString(R.string.pin_app_failed);
                            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    mUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String msg = mContext.getString(R.string.pin_app_successfully);
                            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
                if (d.dragSource instanceof PinSource) {
                    ((PinSource) d.dragSource).onPinApiReturn();
                }
            }
        };
        Thread T = new Thread(doPinAction);
        mProgressDialog.show();
        T.start();
    }

    @Override
    public void onDrop(DragObject d) {
        if (d.dragSource instanceof PinSource) {
            ((PinSource) d.dragSource).onPinApi();
        }
        super.onDrop(d);
    }

    // first return false if package name not match
    public static Pair<Boolean, Boolean> getCachedState(String pkg) {
        if (pkg.equals(mCachedState.first)) {
            return Pair.create(true, mCachedState.second);
        }
        return Pair.create(false, false);
    }

    public static interface PinSource {
        /**
         * on Pin process return
         */
        void onPinApiReturn();
        /**
         * On pin process
         */
        void onPinApi();
    }
}

