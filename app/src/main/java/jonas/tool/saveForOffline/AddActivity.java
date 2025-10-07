/**
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 **/

/**
 This file is part of Save For Offline, an Android app which saves / downloads complete webpages for offine reading.
 **/

/**
 If you modify, redistribute, or write something based on this or parts of it, you MUST,
 I repeat, you MUST comply with the GPLv2+ license. This means that if you use or modify
 my code, you MUST release the source code of your modified version, if / when this is
 required under the terms of the license.

 If you cannot / do not want to do this, DO NOT USE MY CODE. Thanks.

 (I've added this message to to the source because it's been used in severeral proprietary
 closed source apps, which I don't want, and which is also a violation of the liense.)
 **/

/**
 Written by Jonas Czech (JonasCz, stackoverflow.com/users/4428462/JonasCz and github.com/JonasCz). (4428462jonascz/eafc4d1afq)
 **/

package jonas.tool.saveForOffline;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddActivity extends AppCompatActivity {
	private Button btn_save;
	private EditText edit_origurl;
    private TextView tipText;

	private String origurl ;

    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setTitle("Enter URL to save");
		
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setContentView(R.layout.add_activity);

        btn_save = findViewById(R.id.save_btn);
        tipText = findViewById(R.id.tipText);

        edit_origurl = findViewById(R.id.frst_editTxt);
		edit_origurl.setText(getIntent().getStringExtra(Intent.EXTRA_TEXT));

		//save directly if activity was started via intent
		origurl = edit_origurl.getText().toString().trim();
		if (origurl.length() > 0) {
			startSave(origurl);
		}
	}

	public void btn_paste(View view) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		if (clipboard != null && clipboard.hasPrimaryClip()) {
			ClipData clip = clipboard.getPrimaryClip();
			if (clip != null && clip.getItemCount() > 0) {
				CharSequence pasteData = clip.getItemAt(0).getText();
				if (pasteData != null) {
					if (edit_origurl.getText().length() == 0) {
						edit_origurl.append(pasteData);
					} else {
						edit_origurl.append(System.getProperty("line.separator")).append(pasteData);
					}
				}
			}
		}
	}

	// saveButton click event
	public void okButtonClick(View view) {
		origurl = edit_origurl.getText().toString().trim();
        if (isValidUrlText(origurl)) {
            String[] urls = origurl.split("[\\r\\n]+");
            for (String url : urls) {
                startSave(url);
			}
        } else {
            Toast.makeText(this, "Oops, that doesn't look like a valid URL. Make sure you included the 'http://' part.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isValidUrlText(String urltext) {
        String[] urls = urltext.split("[\\r\\n]+");
        for (String url : urls) {
            if (url.length() == 0 || (!url.startsWith("http"))) {
                return false;
            }
        }
        return true;
    }


	private void startSave(String url) {
		Intent intent = new Intent(this, SaveService.class);
		intent.putExtra(Intent.EXTRA_TEXT, url);
		startService(intent);
		finish();
	}
}
