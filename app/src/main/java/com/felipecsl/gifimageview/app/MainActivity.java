package com.felipecsl.gifimageview.app;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.felipecsl.gifimageview.library.GifImageView;


public class MainActivity extends ActionBarActivity implements View.OnClickListener {

    private GifImageView gifImageView;
    private Button btnToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gifImageView = (GifImageView) findViewById(R.id.gifImageView);
        btnToggle = (Button) findViewById(R.id.btnToggle);
        final Button btnClear = (Button) findViewById(R.id.btnClear);

        btnToggle.setOnClickListener(this);
        btnClear.setOnClickListener(this);

        new GifDataDownloader() {
            @Override
            protected void onPostExecute(final byte[] bytes) {
                gifImageView.setBytes(bytes);
                gifImageView.startAnimation();
            }
        }.execute("http://gifs.joelglovier.com/aha/aha.gif");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(final View v) {
        if (v.equals(btnToggle)) {
            if (gifImageView.isAnimating())
                gifImageView.stopAnimation();
            else
                gifImageView.startAnimation();
        } else {
            gifImageView.clear();
        }
    }
}
