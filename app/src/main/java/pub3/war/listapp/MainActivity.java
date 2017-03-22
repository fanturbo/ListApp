package pub3.war.listapp;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private List<ApplicationInfo> mAppList;
    private List<ApplicationInfo> mAppListCopy;
    private RecyclerView mRecycleView;
    private SearchView mSearchApp;
    private String nameFilter;
    private AppAdapter appListAdapter;
    private List<ApplicationInfo> filteredAppList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecycleView = (RecyclerView) findViewById(R.id.recyclerview);
        mSearchApp = (SearchView) findViewById(R.id.searchApp);
        mRecycleView.setLayoutManager(new LinearLayoutManager(this));
        mAppList = new ArrayList<>();
        loadApps();
        appListAdapter = new AppAdapter(this);
        mRecycleView.setAdapter(appListAdapter);
        mSearchApp.setIconified(false);
        mSearchApp.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                nameFilter = query;
                appListAdapter.getFilter().filter(nameFilter);
                mSearchApp.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                nameFilter = newText;
                appListAdapter.getFilter().filter(nameFilter);
                return false;
            }

        });

    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH && (event.getFlags() & KeyEvent.FLAG_CANCELED) == 0) {
            if (mSearchApp.isShown()) {
                mSearchApp.setIconified(false);
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @SuppressLint("DefaultLocale")
    private void loadApps() {
        mAppList.clear();
        PackageManager pm = getPackageManager();
        List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        int i = 1;
        for (PackageInfo pkgInfo : pkgs) {
            ApplicationInfo appInfo = pkgInfo.applicationInfo;
            if (appInfo == null)
                continue;
            appInfo.name = appInfo.loadLabel(pm).toString();
            mAppList.add(appInfo);
        }

        Collections.sort(mAppList, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo lhs, ApplicationInfo rhs) {
                if (lhs.name == null) {
                    return -1;
                } else if (rhs.name == null) {
                    return 1;
                } else {
                    return lhs.name.toUpperCase().compareTo(rhs.name.toUpperCase());
                }
            }
        });
        mAppListCopy= new ArrayList<>();
        mAppListCopy.addAll(mAppList);
    }

    class AppAdapter extends BaseQuickAdapter<ApplicationInfo, BaseViewHolder> {

        private Context mContext;
        private Filter mFilter;

        public AppAdapter(Context context) {
            super(R.layout.item_app_list, mAppList);
            mContext = context;
            mFilter = new AppListFilter(this);
            ;
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        protected void convert(final BaseViewHolder baseViewHolder, final ApplicationInfo applicationInfo) {
            baseViewHolder.setText(R.id.app_name, applicationInfo.name)
                    .setText(R.id.app_package, applicationInfo.packageName);
            baseViewHolder.getView(R.id.app_icon).setBackground(mContext.getResources().getDrawable(R.mipmap.ic_launcher));
            Observable
                    .create(new Observable.OnSubscribe<Drawable>() {
                        @Override
                        public void call(Subscriber<? super Drawable> subscriber) {
                            Drawable drawable = applicationInfo.loadIcon(mContext.getPackageManager());
                            subscriber.onNext(drawable);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<Drawable>() {
                        @Override
                        public void call(Drawable drawable) {
                            baseViewHolder.getView(R.id.app_icon).setBackground(drawable);
                        }
                    });

        }

        public Filter getFilter() {
            return mFilter;
        }
    }


    private class AppListFilter extends Filter {

        private AppAdapter adapter;

        AppListFilter(AppAdapter adapter) {
            super();
            this.adapter = adapter;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            // NOTE: this function is *always* called from a background thread, and
            // not the UI thread.

            ArrayList<ApplicationInfo> items = new ArrayList<ApplicationInfo>();
            synchronized (this) {
                items.addAll(mAppListCopy);
            }

            SharedPreferences prefs = getSharedPreferences("SPSettings", Context.MODE_WORLD_READABLE);

            FilterResults result = new FilterResults();
            if (constraint != null && constraint.length() > 0) {
                Pattern regexp = Pattern.compile(constraint.toString(), Pattern.LITERAL | Pattern.CASE_INSENSITIVE);
                for (Iterator<ApplicationInfo> i = items.iterator(); i.hasNext(); ) {
                    ApplicationInfo app = i.next();
                    if (!regexp.matcher(app.name == null ? "" : app.name).find()
                            && !regexp.matcher(app.packageName).find()) {
                        i.remove();
                    }
                }
            }
            for (Iterator<ApplicationInfo> i = items.iterator(); i.hasNext(); ) {
                ApplicationInfo app = i.next();
                if (filteredOut(prefs, app))
                    i.remove();
            }

            result.values = items;
            result.count = items.size();

            return result;
        }

        private boolean filteredOut(SharedPreferences prefs, ApplicationInfo app) {
            String packageName = app.packageName;
            boolean isUser = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            return false;
        }


        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // NOTE: this function is *always* called from the UI thread.
            filteredAppList = (ArrayList<ApplicationInfo>) results.values;
            adapter.getData().clear();
            adapter.notifyDataSetChanged();
            for (int i = 0, l = filteredAppList.size(); i < l; i++) {
                adapter.add(i, filteredAppList.get(i));
            }
            adapter.notifyDataSetChanged();
        }
    }

}
