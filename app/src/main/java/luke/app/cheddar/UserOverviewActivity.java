package luke.app.cheddar;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import net.dean.jraw.RedditClient;
import net.dean.jraw.models.Account;
import net.dean.jraw.models.PersistedAuthData;
import net.dean.jraw.oauth.DeferredPersistentTokenStore;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Shows some very simple information about the currently authenticated user. Currently, it shows
 * their username, link karma, comment karma, access token expiration, and if we have a refresh
 * token for them.
 */
public class UserOverviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_overview);

        // Fetch the user's account information
        new GetUserInfoTask(this).execute(CheddarApplication.Companion.getInstance().getAccountHelper().getReddit());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // Provide the user two ways to log out: the "log out" button, and exiting this activity
        onLogout(null);
    }

    private void show(Account account) {
        // Basics
        textView(R.id.username).setText(account.getName());
        textView(R.id.linkKarma).setText(getString(R.string.link_karma, formatInt(account.getLinkKarma())));
        textView(R.id.commentKarma).setText(getString(R.string.comment_karma, formatInt(account.getCommentKarma())));

        // OAuth2 stuff
        DeferredPersistentTokenStore tokenStore = CheddarApplication.Companion.getInstance().getTokenStore();
        PersistedAuthData data = tokenStore.inspect(account.getName());
        if (data != null) {
            if (data.getLatest() != null) {
                // Calculate the amount of minutes in which the OAuthData will expire
                long diffMillis = data.getLatest().getExpiration().getTime() - new Date().getTime();
                long diffMinutes = TimeUnit.MINUTES.convert(diffMillis, TimeUnit.MILLISECONDS);

                // Update the TextView with this information
                textView(R.id.accessTokenStatus).setText(getString(R.string.access_token_status, diffMinutes));
            }

            // Show whether or not the data includes a refresh token
            textView(R.id.refreshToken).setText(data.getRefreshToken() == null ?
                    R.string.no_refresh_token : R.string.refresh_token);
        }
    }

    private TextView textView(@IdRes int id) {
        return (TextView) findViewById(id);
    }

    private static String formatInt(int n) {
        return NumberFormat.getInstance().format(n);
    }

    public void onLogout(View view) {
        // All this does is remove the current RedditClient reference. If we tried to do
        // App.getAccountHelper().getReddit(), it would throw an IllegalStateException.
        CheddarApplication.Companion.getInstance().getAccountHelper().logout();
        finish();
    }

    private static final class GetUserInfoTask extends AsyncTask<RedditClient, Void, Account> {
        // Use a WeakReference to avoid leaking a Context
        private final WeakReference<UserOverviewActivity> activity;

        GetUserInfoTask(UserOverviewActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            ProgressBar progressBar = getProgressBar();
            if (progressBar != null)
                progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Account doInBackground(RedditClient... redditClients) {
            return redditClients[0].me().about();
        }

        @Override
        protected void onPostExecute(Account account) {
            // Display the fetched account if the Activity still exists
            UserOverviewActivity activity = this.activity.get();
            if (activity != null)
                activity.show(account);

            // Prefer INVISIBLE instead of GONE so everything doesn't get shifted up a few pixels
            // once loading is done
            ProgressBar progressBar = getProgressBar();
            if (progressBar != null)
                progressBar.setVisibility(View.INVISIBLE);
        }

        private ProgressBar getProgressBar() {
            if (this.activity.get() != null)
                return this.activity.get().findViewById(R.id.progress);
            return null;
        }
    }
}
