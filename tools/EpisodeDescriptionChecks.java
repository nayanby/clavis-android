package com.clavis.reader;
final class EpisodeDescriptionChecks {
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  String text=EpisodeDescription.clean("<p>We discuss <a href='https://research.example/paper'>climate research</a> with our guest.</p><p>00:00 Introduction<br>12:30 The evidence</p><p>Follow us on Instagram: https://social.example/show</p><p>This episode is sponsored by Brand.</p><p>Learn more about your ad choices. Visit https://ads.example</p><footer>Support the show</footer>");
  check(text.contains("climate research")&&text.contains("with our guest"),"Episode prose and meaningful linked words remain");
  check(text.contains("00:00 Introduction")&&text.contains("12:30 The evidence"),"Chapter timestamps remain");
  check(!text.contains("Instagram")&&!text.contains("sponsored")&&!text.contains("ad choices")&&!text.contains("Support the show"),"Promotional blocks removed");
  check(!text.contains("https://"),"Raw links removed");
  check(EpisodeDescription.clean("A useful summary.\n\nSubscribe to our newsletter.\n\nAdditional episode notes.").equals("A useful summary.\n\nAdditional episode notes."),"Plain-text footers removed without losing later notes");
  check(EpisodeDescription.clean("&lt;p&gt;Episode details.&lt;/p&gt;").equals("Episode details."),"Escaped HTML handled");
  check(EpisodeDescription.clean(null).isEmpty(),"Empty descriptions supported");
  System.out.println("7 episode description cleanup checks passed");
 }
}
