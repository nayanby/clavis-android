package com.clavis.reader;
import android.app.*;
import android.content.*;
import android.widget.*;
import java.net.URI;
final class BrowserSettings {
 static final String DEFAULT="https://librivox.org/";
 static final String EBOOK_DEFAULT="https://www.gutenberg.org/";
 static String home(Context c,boolean ebook){String value=c.getSharedPreferences("browser",0).getString(ebook?"ebookWebsite":"website",ebook?EBOOK_DEFAULT:DEFAULT);return value.matches("(?i).*audiobookbay.*|.*z-library.*|.*z-lib\\..*")?(ebook?EBOOK_DEFAULT:DEFAULT):value;}
 static String home(Context c){return home(c,false);}
 static String validate(String input){String value=input.trim();if(!value.contains("://"))value="https://"+value;URI uri=URI.create(value);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Enter an HTTPS website address, for example https://librivox.org/");return uri.toASCIIString();}
 static void show(MainActivity a){show(a,false);}
 static void show(MainActivity a,boolean ebook){LinearLayout panel=a.column();panel.setPadding(a.dp(20),a.dp(8),a.dp(20),0);EditText input=new EditText(a);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setSingleLine();input.setTextColor(MainActivity.INK);input.setText(home(a,ebook));panel.addView(input);TextView help=a.text(ebook?"Choose the website opened by Browse ebooks. PDF and EPUB downloads are added to your library.":"Choose the website opened by Browse audiobooks. Magnet links open in Clavis Downloads.",12,MainActivity.MUTED,false);help.setPadding(0,a.dp(10),0,0);panel.addView(help);AlertDialog dialog=new AppDialog.Builder(a).setTitle(ebook?"Ebook website":"Audiobook website").setView(panel).setPositiveButton("Save",null).setNeutralButton("Reset",null).setNegativeButton("Cancel",null).create();dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String url=validate(input.getText().toString());a.getSharedPreferences("browser",0).edit().putString(ebook?"ebookWebsite":"website",url).apply();dialog.dismiss();}catch(Exception e){input.setError(e.getMessage());}});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->input.setText(ebook?EBOOK_DEFAULT:DEFAULT));});dialog.show();}
}

