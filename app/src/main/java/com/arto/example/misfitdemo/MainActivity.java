package com.arto.example.misfitdemo;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import com.github.scribejava.apis.MisfitApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends Activity
{
    private final static String TAG = "MistFitMainActivity";

    private static String CLIENT_ID = "your client id";
    private static String CLIENT_SECRET = "your client secret";
    private static String REDIRECT_URI = "http://example.com/callback/";
    private static String MISFIT_SCOPE = "public,birthday,email,tracking,session,sleep";

    WebView webView;
    Button authButton;
    SharedPreferences pref;
    OAuth20Service service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        service = new ServiceBuilder()
                .apiKey(CLIENT_ID)
                .apiSecret(CLIENT_SECRET)
                .callback(REDIRECT_URI)
                .scope(MISFIT_SCOPE)
                .build(MisfitApi.instance());

        // Check if the accessToken was previously saved and if so fetch data right away
        pref = getSharedPreferences("AppPref", MODE_PRIVATE);
        String accessToken = pref.getString("accessToken", null);
        if (accessToken != null) {
            OAuth2AccessToken token = new OAuth2AccessToken(accessToken);
            new asyncFetchData().execute(token);
        }

        authButton = (Button)findViewById(R.id.authorizeBtn);
        authButton.setOnClickListener(new View.OnClickListener()
        {
            Dialog auth_dialog;

            @Override
            public void onClick(View arg0)
            {
                auth_dialog = new Dialog(MainActivity.this);
                auth_dialog.setContentView(R.layout.web_dialog);
                webView = (WebView)auth_dialog.findViewById(R.id.webview);
                webView.loadUrl(service.getAuthorizationUrl());

                webView.setWebViewClient(new WebViewClient() {
                    Intent resultIntent = new Intent();
                    String authCode;

                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        if (url.contains("?code=")) {
                            Uri uri = Uri.parse(url);
                            authCode = uri.getQueryParameter("code");
                            resultIntent.putExtra("code", authCode);

                            MainActivity.this.setResult(Activity.RESULT_OK, resultIntent);
                            setResult(Activity.RESULT_CANCELED, resultIntent);
                            auth_dialog.dismiss();
                            new asyncFetchToken().execute(authCode);

                        } else if (url.contains("error=access_denied")) {
                            Log.i("TAG", "access_denied");
                            setResult(Activity.RESULT_CANCELED, resultIntent);
                            auth_dialog.dismiss();
                        }
                    }
                });
                auth_dialog.show();
                auth_dialog.setTitle("Authorize Misfit");
                auth_dialog.setCancelable(true);
            }
        });
    }

    class asyncFetchToken extends AsyncTask<String, Void, OAuth2AccessToken> {

        @Override
        protected OAuth2AccessToken doInBackground(String... params) {

            String authCode = params[0];
            OAuth2AccessToken accessToken = service.getAccessToken(authCode);

            SharedPreferences.Editor edit = pref.edit();
            edit.putString("accessToken", accessToken.getAccessToken());
            edit.apply();

            return accessToken;
        }

        @Override
        protected void onPostExecute(OAuth2AccessToken token) {
            new asyncFetchData().execute(token);
        }
    }

    class asyncFetchData extends AsyncTask<OAuth2AccessToken, Void, String> {

        @Override
        protected String doInBackground(OAuth2AccessToken... params) {

            OAuth2AccessToken accessToken = params[0];
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");

            cal.add(Calendar.DATE, -1);
            Date date = cal.getTime();
            String dateNow = ft.format(date);
            cal.add(Calendar.DATE, -30);
            date = cal.getTime();
            String datePast = ft.format(date);

            String dataUrl = "https://api.misfitwearables.com/move/resource/v1/user/me/activity/summary?start_date=" + datePast + "&end_date=" + dateNow;

            OAuthRequest request = new OAuthRequest(Verb.GET, dataUrl, service);
            service.signRequest(accessToken, request);
            Response response = request.send();

            return response.getBody();
        }

        @Override
        protected void onPostExecute(String result) {

            TextView output = (TextView) findViewById(R.id.misfitOutput);

            try {
                JSONObject obj = new JSONObject(result);
                Integer averageSteps = obj.getInt("steps") / 30;
                output.setText("Daily average for the past month: " + averageSteps.toString());

            } catch (Throwable t) {
                Log.i("TAG", "Failed to parse json: " + result);
            }
        }
    }
}
