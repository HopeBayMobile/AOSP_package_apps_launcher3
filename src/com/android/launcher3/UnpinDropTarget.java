package com.android.launcher3;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.Toast;

import com.android.launcher3.util.Thunk;

public class UnpinDropTarget extends ButtonDropTarget {

    private static final String TAG = "UnpinDropTarget";

    private static TeraApiService mTeraApiService;
    private Handler mUIHandler;
    private Context mContext;
    @Thunk Launcher mLauncher;
    private static ProgressDialog mProgressDialog;

    public UnpinDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UnpinDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mLauncher =  (Launcher) context;
        mUIHandler = new Handler(mLauncher.getMainLooper());
        mProgressDialog = new ProgressDialog(context);
        mProgressDialog.setMessage(mContext.getString(R.string.unpin_app_in_progress));
        mTeraApiService = TeraApiService.getInstance(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.unpin_target_hover_tint);
        setDrawable(R.drawable.ic_unpin_launcher);
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
        Pair<Boolean, Boolean> cached = PinDropTarget.getCachedState(packageName);
        boolean pinned = false;
        if (cached.first)
            pinned = cached.second;
        else
            pinned = mTeraApiService.isAppPinned(packageName);
        return mTeraApiService.isAllowPinUnpinApps()
                && pinned && !mTeraApiService.isBoostOrUnboostInProgress(packageName);
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
        final Runnable doUnpinAction = new Runnable() {
            @Override
            public void run() {
                String packageName = componentInfo.first.getPackageName();
                boolean isSuccess = mTeraApiService.unpinApp(packageName);
                mProgressDialog.dismiss();
                if (!isSuccess) {
                    mUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String msg = mContext.getString(R.string.unpin_app_failed);
                            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    mUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String msg = mContext.getString(R.string.unpin_app_successfully);
                            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
                if (d.dragSource instanceof UnpinSource) {
                    ((UnpinSource) d.dragSource).onUnpinApiReturn();
                }
            }
        };
        Thread T = new Thread(doUnpinAction);
        mProgressDialog.show();
        T.start();
    }

    @Override
    public void onDrop(DragObject d) {
        if (d.dragSource instanceof UnpinSource) {
            ((UnpinSource) d.dragSource).onUnpinApi();
        }
        super.onDrop(d);
    }

    public static interface UnpinSource {
        /**
         * on Pin process return
         */
        void onUnpinApiReturn();
        /**
         * On pin process
         */
        void onUnpinApi();
    }
}