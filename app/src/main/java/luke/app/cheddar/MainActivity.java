package luke.app.cheddar;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.view.ViewGroup;

import net.dean.jraw.models.PersistedAuthData;
import net.dean.jraw.oauth.DeferredPersistentTokenStore;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The main activity is a list of all stored authentication data. This data is provided from
 * App.getTokenStore(). When an item in the list is clicked, we attempt to authenticate ourselves as
 * that user. If we have an unexpired access token, we use that. If we only have a refresh token,
 * we use that and request a fresh access token on the next normal request. After we authenticate,
 * the UserOverviewActivity is started.
 *
 * This activity has a FAB in the bottom right-hand corner for authenticating new users. When
 * pressed, the NewUserActivity is started. See that class' documentation for what it does.
 */
public class MainActivity extends AppCompatActivity {
    private static final int REQ_CODE_LOGIN = 0;

    private RecyclerView storedDataList;
    private AuthDataAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create the RecyclerView's LayoutManager and Adapter
        this.storedDataList = findViewById(R.id.storedDataList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        this.adapter = new AuthDataAdapter(this, storedDataList, CheddarApplication.Companion.getInstance().getTokenStore());

        // Configure the RecyclerView
        storedDataList.setLayoutManager(layoutManager);
        storedDataList.setAdapter(adapter);
        storedDataList.addItemDecoration(new DividerItemDecoration(this, layoutManager.getOrientation()));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The data in the TokenStore might have changed, let's update the RecyclerView
        adapter.update();

        // Show the RecyclerView if there's data, otherwise show a message
        boolean hasData = CheddarApplication.Companion.getInstance().getTokenStore().size() == 0;
        storedDataList.setVisibility(hasData ? View.GONE : View.VISIBLE);
        findViewById(R.id.noDataMessage).setVisibility(hasData ? View.VISIBLE : View.GONE);
    }

    // Called when the FAB is clicked
    public void onNewUserRequested(View view) {
        startActivityForResult(new Intent(this, NewUserActivity.class), REQ_CODE_LOGIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user could have pressed the back button before authorizing our app, make sure we have
        // an authenticated user before starting the UserOverviewActivity.
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_LOGIN && resultCode == RESULT_OK) {
            startActivity(new Intent(this, UserOverviewActivity.class));
        }
    }

    /**
     * This Adapter pulls its data from a TokenStore
     */
    private static class AuthDataAdapter extends RecyclerView.Adapter<AuthDataViewHolder> {
        private final WeakReference<MainActivity> activity;
        private final DeferredPersistentTokenStore tokenStore;
        private List<String> usernames;
        private TreeMap<String, PersistedAuthData> data;
        private RecyclerView recyclerView;

        private AuthDataAdapter(MainActivity mainActivity, RecyclerView recyclerView, DeferredPersistentTokenStore tokenStore) {
            this.activity = new WeakReference<>(mainActivity);
            this.recyclerView = recyclerView;
            this.tokenStore = tokenStore;
            update();
        }

        private void update() {
            this.data = new TreeMap<>(tokenStore.data());

            // Prefer this instead of tokenStore.getUsernames() because this.data.keySet() is sorted
            this.usernames = new ArrayList<>(this.data.keySet());
            notifyDataSetChanged();
        }

        @Override
        public AuthDataViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // Create a new TokenStoreUserView when requested
            TokenStoreUserView view = new TokenStoreUserView(parent.getContext());

            // Give the view max width and minimum height
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // Listen for the view being clicked
            view.setOnClickListener(clickedView -> {
                int itemPos = recyclerView.getChildLayoutPosition(clickedView);

                // Potential bug: Clicking on two different items in a very short period of time
                // could cause this task to be executed twice
                new ReauthenticationTask(activity).execute(usernames.get(itemPos));
            });

            return new AuthDataViewHolder(view);
        }

        @Override
        public void onBindViewHolder(AuthDataViewHolder holder, int position) {
            // Tell the TokenStoreUserView to change the data it's showing when the view holder gets
            // recycled
            String username = this.usernames.get(position);
            holder.view.display(username, data.get(username));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private static class AuthDataViewHolder extends RecyclerView.ViewHolder {
        private TokenStoreUserView view;

        private AuthDataViewHolder(TokenStoreUserView itemView) {
            super(itemView);
            this.view = itemView;
        }
    }

    private static class ReauthenticationTask extends AsyncTask<String, Void, Void> {
        private final WeakReference<MainActivity> activity;

        ReauthenticationTask(WeakReference<MainActivity> activity) {
            this.activity = activity;
        }

        @Override
        protected Void doInBackground(String... usernames) {
            CheddarApplication.Companion.getInstance().getAccountHelper().switchToUser(usernames[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Activity activity = this.activity.get();

            if (activity != null) {
                activity.startActivity(new Intent(activity, UserOverviewActivity.class));
            }
        }
    }
}
