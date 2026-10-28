package com.clavis.reader;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;
import java.util.*;

/** Vertical RSS posts, with a left-swipe image/text carousel inside each story. */
final class FeedPosts {
 static void show(MainActivity a,FeedStore.Feed feed){
  a.openFeed=feed;a.screen=feed.kind+" feed";a.shell(feed.title);a.bar.getChildAt(0).setOnClickListener(v->FeedScreens.show(a,feed.kind,true));a.bar.removeViewAt(2);
  a.bar.addView(UiStyle.icon(a,R.drawable.ic_toolbar_more,"Feed actions",()->new AppDialog.Builder(a).setTitle(feed.title).setItems(new String[]{"Refresh feed","Browse sources"},(d,n)->{if(n==0)FeedScreens.refreshSource(a,feed);else FeedCatalog.browse(a,feed.kind);}).show(),48));
  attach(a,feed);
 }
 static void attach(MainActivity a,FeedStore.Feed feed){
  View old=a.root.getChildAt(1);a.root.removeView(old);
  final PostAdapter[] holder={null};RecyclerView posts=new RecyclerView(a){@Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(holder[0]!=null)holder[0].close();}};
  posts.setContentDescription("News posts. Scroll vertically for stories; swipe left within a post to read it.");posts.setLayoutManager(new LinearLayoutManager(a));posts.setItemAnimator(null);posts.setClipToPadding(false);
  int gutter=a.dp(Math.max(12,(a.getResources().getConfiguration().screenWidthDp-560)/2));posts.setPadding(gutter,a.dp(10),gutter,a.dp(104));
  PostAdapter adapter=new PostAdapter(a,feed);holder[0]=adapter;posts.setAdapter(adapter);a.root.addView(FeedRefresh.wrap(a,posts,feed.kind,feed.url.isEmpty()?null:feed),new LinearLayout.LayoutParams(-1,0,1));
  if(feed.items.isEmpty()){TextView empty=a.text("No stories yet. Use Feed actions to refresh this feed.",16,MainActivity.MUTED,false);empty.setPadding(a.dp(20),a.dp(24),a.dp(20),a.dp(24));a.root.addView(empty,1);}
  a.miniPlayer();
 }
 static final class State {
  final FeedStore.Feed feed;final FeedStore.Item item;final ArticleContent fallback;ArticleContent article;String body,lead,status="";int current;boolean loading,loaded;
  ArrayList<StoryPages.Page> pages=new ArrayList<>();
  State(FeedStore.Feed feed,FeedStore.Item item){this.feed=feed;this.item=item;fallback=ArticleContent.extract(item.description,item.link.isEmpty()?feed.url:item.link,true);article=fallback;body=StoryPages.text(fallback);lead=PostArtwork.usable(item.image,feed)?item.image:PostArtwork.choose(fallback,feed);if(feed.fullText&&!body.isEmpty())loaded=true;}
 }
 static final class SwipeCard extends LinearLayout {
  ViewPager2 pager;float downX,downY;boolean horizontal;final int slop;
  SwipeCard(MainActivity a){super(a);slop=ViewConfiguration.get(a).getScaledTouchSlop();}
  @Override public boolean onInterceptTouchEvent(MotionEvent e){
   if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();horizontal=false;}
   if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-downX)>slop&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)*1.3f){horizontal=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
   return super.onInterceptTouchEvent(e);
  }
  @Override public boolean performClick(){return super.performClick();}
  @Override public boolean onTouchEvent(MotionEvent e){
   if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-downX)>slop&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)*1.3f){horizontal=true;getParent().requestDisallowInterceptTouchEvent(true);}
   if(!horizontal){if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
   if(e.getActionMasked()==MotionEvent.ACTION_UP){float delta=e.getX()-downX;if(Math.abs(delta)>slop*2&&pager!=null&&pager.getAdapter()!=null)pager.setCurrentItem(Math.max(0,Math.min(pager.getAdapter().getItemCount()-1,pager.getCurrentItem()+(delta<0?1:-1))),true);horizontal=false;getParent().requestDisallowInterceptTouchEvent(false);}
   else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){horizontal=false;getParent().requestDisallowInterceptTouchEvent(false);}return true;
  }
 }
 static final class PostAdapter extends RecyclerView.Adapter<PostHolder> {
  final MainActivity a;final FeedStore.Feed feed;final android.view.View screen;final ArrayList<State> states=new ArrayList<>();boolean closed;
  PostAdapter(MainActivity a,FeedStore.Feed feed){this.a=a;this.feed=feed;screen=a.mainFrame;for(int n=0;n<feed.items.size();n++)states.add(null);}
  @Override public int getItemCount(){return feed.items.size();}
  @Override public PostHolder onCreateViewHolder(ViewGroup parent,int type){return new PostHolder(this);}
  @Override public void onBindViewHolder(PostHolder view,int position){State state=states.get(position);if(state==null){state=new State(feed.origins.getOrDefault(feed.items.get(position),feed),feed.items.get(position));states.set(position,state);}view.bind(state);load(view,state);for(int n=position+1;n<Math.min(states.size(),position+7);n++){State upcoming=states.get(n);if(upcoming==null){FeedStore.Item item=feed.items.get(n);upcoming=new State(feed.origins.getOrDefault(item,feed),item);states.set(n,upcoming);}FeedArtwork.prefetch(a,upcoming.lead);}}
  @Override public void onViewRecycled(PostHolder holder){if(holder.heightAnimation!=null)holder.heightAnimation.cancel();holder.bound=false;holder.pager.setAdapter(null);holder.state=null;}
  void close(){closed=true;}
  void load(PostHolder holder,State state){
   if(state.loaded||state.loading||closed)return;if(state.item.link.isEmpty()){state.loaded=true;state.status="Feed text · no publisher link";holder.update();return;}
   state.loading=true;state.status="Loading full story…";holder.update();
   try{ArticleRepository.request(a,state.item.link,state.fallback).whenComplete((result,error)->{
    a.runOnUiThread(()->{if(closed||a.isDestroyed()||a.isFinishing()||a.mainFrame!=screen)return;state.loading=false;state.loaded=true;
     if(result!=null){state.article=result;if(state.item.date.isEmpty())state.item.date=result.published;state.body=StoryPages.text(result);state.status=result.preview?"Publisher preview · Open website for access":"";if(state.lead.isEmpty())state.lead=PostArtwork.choose(result,state.feed);}
     else state.status="Feed text · full story may be on the website";
     if(holder.state==state){holder.paginate();holder.update();}
    });});}catch(java.util.concurrent.RejectedExecutionException e){state.loading=false;state.status="Feed text · open the article for more";holder.update();}
  }
 }
 static int viewportHeight(MainActivity a,boolean cover){int base=cover?Math.max(260,Math.min(400,Math.round(a.getResources().getConfiguration().screenHeightDp*.43f))):Math.max(340,Math.min(620,Math.round(a.getResources().getConfiguration().screenHeightDp*.62f)));return a.dp(Math.round(base*.8f));}
 static final class PostHolder extends RecyclerView.ViewHolder {
  final PostAdapter owner;final MainActivity a;final LinearLayout card;final ViewPager2 pager;final TextView source,published,title,counter,status,read,credits;final ImageButton previous,next;State state;Slides slides;boolean coverHasImage,bound;int targetHeight=-1;android.animation.ValueAnimator heightAnimation;
  PostHolder(PostAdapter owner){super(new SwipeCard(owner.a));this.owner=owner;a=owner.a;card=(LinearLayout)itemView;card.setOrientation(LinearLayout.VERTICAL);card.setPadding(a.dp(14),a.dp(14),a.dp(14),a.dp(14));card.setBackground(UiStyle.surface(a,owner.feed.kind,18));
   RecyclerView.LayoutParams cp=new RecyclerView.LayoutParams(-1,-2);cp.bottomMargin=a.dp(18);card.setLayoutParams(cp);
   LinearLayout metadata=a.row();metadata.setGravity(Gravity.CENTER_VERTICAL);metadata.setPadding(0,0,0,a.dp(10));source=a.text(FeedScreens.plain(owner.feed.title),13,MainActivity.MUTED,false);source.setMaxLines(2);source.setEllipsize(TextUtils.TruncateAt.END);source.setPadding(0,0,a.dp(8),0);published=a.text("",11,MainActivity.MUTED,false);published.setGravity(Gravity.END);published.setSingleLine(true);metadata.addView(source,new LinearLayout.LayoutParams(0,-2,1));metadata.addView(published,new LinearLayout.LayoutParams(-2,-2));card.addView(metadata,new LinearLayout.LayoutParams(-1,-2));
   pager=new ViewPager2(a);pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);pager.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);pager.setOffscreenPageLimit(1);
   ((SwipeCard)card).pager=pager;
   card.addView(pager,new LinearLayout.LayoutParams(-1,viewportHeight(a,true)));
   title=a.text("",21,MainActivity.INK,true);title.setTypeface(a.serif,Typeface.BOLD);title.setMaxLines(4);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,a.dp(12),0,a.dp(4));card.addView(title);
   LinearLayout navigation=a.row();previous=UiStyle.icon(a,R.drawable.ic_chevron_left,"Previous story slide",()->pager.setCurrentItem(Math.max(0,pager.getCurrentItem()-1),true),48);next=UiStyle.icon(a,R.drawable.ic_chevron_right,"Next story slide",()->pager.setCurrentItem(Math.min(slides.getItemCount()-1,pager.getCurrentItem()+1),true),48);
   counter=a.text("",13,MainActivity.MUTED,false);counter.setGravity(Gravity.CENTER);counter.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);navigation.addView(previous);navigation.addView(counter,new LinearLayout.LayoutParams(0,-2,1));navigation.addView(next);card.addView(navigation);
   status=a.text("",12,MainActivity.MUTED,false);status.setGravity(Gravity.CENTER);card.addView(status);
   credits=a.text("",11,MainActivity.MUTED,false);credits.setMaxLines(2);credits.setEllipsize(TextUtils.TruncateAt.END);credits.setPadding(0,a.dp(6),0,0);credits.setOnClickListener(v->{if(state!=null)FeedCatalog.credits(a,state.feed,state.item);});card.addView(credits);
   read=a.button("Open website",()->{if(state!=null&&!state.item.link.isEmpty())a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(state.item.link)));});read.setTextSize(14);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=a.dp(8);LinearLayout actions=a.row();TextView listen=a.button("Listen",()->{if(state!=null&&state.loading){AppNotice.makeText(a,"Wait for the full story to finish loading.",Toast.LENGTH_SHORT).show();return;}if(state!=null)Listen.article(a,"article:"+(state.item.link.isEmpty()?state.feed.url+":"+state.item.id:state.item.link),FeedScreens.plain(state.item.title),state.body);});listen.setTextSize(14);listen.setContentDescription("Listen to story");actions.addView(listen,new LinearLayout.LayoutParams(0,-2,1));actions.addView(read,new LinearLayout.LayoutParams(0,-2,1));card.addView(actions,rp);
   pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback(){@Override public void onPageSelected(int position){if(state==null)return;if(position>0&&state.current==0&&pager.getScrollState()!=ViewPager2.SCROLL_STATE_IDLE)FeedHighlights.opened(a,state.item);state.current=position;update();}});
   pager.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(state!=null&&r-l!=or-ol)pager.post(()->{if(state!=null)paginate();});});
  }
  void bind(State value){bound=false;if(heightAnimation!=null)heightAnimation.cancel();targetHeight=-1;coverHasImage=false;int restored=value.current;state=value;source.setText(FeedScreens.plain(value.feed.title));published.setText(FeedDate.display(value.item.date,a.getResources().getConfiguration().getLocales().get(0)));published.setContentDescription("Published "+published.getText().toString().replace('\n',' '));title.setText(FeedScreens.plain(value.item.title));HindiTypography.apply(title,title.getText().toString(),Typeface.create(a.serif,Typeface.BOLD));HindiTypography.apply(source,source.getText().toString(),a.serif);title.setContentDescription(FeedScreens.plain(value.item.title));read.setVisibility(value.item.link.isEmpty()?View.GONE:View.VISIBLE);slides=new Slides(this);pager.setAdapter(slides);paginate();pager.setCurrentItem(Math.min(restored,slides.getItemCount()-1),false);update();bound=true;}
  void paginate(){if(state==null||pager.getWidth()<=0||pager.getHeight()<=0)return;if(((RecyclerView)pager.getChildAt(0)).isComputingLayout()){pager.post(this::paginate);return;}TextView sample=paragraph(a);HindiTypography.apply(sample,state.body);state.pages=StoryPages.paginate(state.body,sample.getPaint(),Math.max(1,pager.getWidth()-a.dp(36)),Math.max(1,viewportHeight(a,false)-a.dp(44)),a.dp(3),1.16f,a.dp(16));int page=Math.min(state.current,slides.getItemCount()-1);slides.notifyDataSetChanged();pager.setCurrentItem(page,false);update();}
  int compactCoverHeight(){String heading=FeedScreens.plain(state.item.title);TextView sample=a.text(heading,23,MainActivity.INK,true);sample.setTypeface(a.serif,Typeface.BOLD);HindiTypography.apply(sample,heading);int width=pager.getWidth()>0?pager.getWidth():a.getResources().getDisplayMetrics().widthPixels-a.dp(52);android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(heading,0,heading.length(),sample.getPaint(),Math.max(1,width-a.dp(48))).setIncludePad(true).setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE).build();return Math.min(viewportHeight(a,true),Math.max(a.dp(96),layout.getHeight()+a.dp(48)));}
  void resizeViewport(int height){if(targetHeight==height)return;targetHeight=height;if(heightAnimation!=null)heightAnimation.cancel();int current=pager.getLayoutParams().height;if(!bound||!pager.isLaidOut()||current<=0){pager.getLayoutParams().height=height;pager.requestLayout();return;}heightAnimation=android.animation.ValueAnimator.ofInt(current,height);heightAnimation.setDuration(220);heightAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator());heightAnimation.addUpdateListener(value->{pager.getLayoutParams().height=(Integer)value.getAnimatedValue();pager.requestLayout();});heightAnimation.start();}
  void update(){if(state==null||slides==null)return;published.setTag(state.item);FeedDate.bind(published,state.item);String credit=(state.item.author.isEmpty()?"":"By "+FeedScreens.plain(state.item.author))+(state.item.rights.isEmpty()?"":(state.item.author.isEmpty()?"":" · ")+FeedScreens.plain(state.item.rights));if(credit.isEmpty()&&!state.feed.policy.isEmpty())credit="Source and licensing";credits.setText(credit);credits.setContentDescription(credit+". Open source credits");credits.setVisibility(credit.isEmpty()?View.GONE:View.VISIBLE);int page=pager.getCurrentItem(),count=slides.getItemCount();title.setVisibility(page==0&&coverHasImage?View.VISIBLE:View.GONE);int height=page==0&&!coverHasImage?compactCoverHeight():viewportHeight(a,page==0);resizeViewport(height);counter.setText((page+1)+" / "+count+(page==0?" · Swipe left to read":""));status.setText(state.status);status.setVisibility(state.status.isEmpty()?View.GONE:View.VISIBLE);previous.setEnabled(page>0);previous.setAlpha(page>0?1f:.4f);next.setEnabled(page<count-1);next.setAlpha(page<count-1?1f:.4f);}
 }
 static TextView paragraph(MainActivity a){TextView text=a.text("",18,MainActivity.INK,false);text.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);text.setGravity(Gravity.TOP|Gravity.START);text.setIncludeFontPadding(true);if(android.os.Build.VERSION.SDK_INT>=28)text.setFallbackLineSpacing(true);text.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE);text.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);text.setLineSpacing(a.dp(3),1.16f);text.setPadding(a.dp(18),a.dp(18),a.dp(18),a.dp(18));return text;}
 // This image view belongs to the platform Activity, which supplies its own theme and tint.
 @android.annotation.SuppressLint("AppCompatCustomView")
 static final class Slide extends RecyclerView.ViewHolder {
  final FrameLayout frame;final TextView text,placeholder;final ImageView image;PostHolder post;
  Slide(MainActivity a){super(new FrameLayout(a));frame=(FrameLayout)itemView;frame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);frame.setLayoutParams(new RecyclerView.LayoutParams(-1,-1));GradientDrawable bg=new GradientDrawable();bg.setColor(AppTheme.raised);bg.setCornerRadius(a.dp(14));frame.setBackground(bg);frame.setClipToOutline(true);
   placeholder=a.text("",23,MainActivity.INK,true);placeholder.setTypeface(a.serif,Typeface.BOLD);placeholder.setAutoSizeTextTypeUniformWithConfiguration(16,23,1,android.util.TypedValue.COMPLEX_UNIT_SP);placeholder.setPadding(a.dp(24),a.dp(24),a.dp(24),a.dp(24));placeholder.setGravity(Gravity.CENTER);frame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
   image=new ImageView(a){@Override public void setImageBitmap(Bitmap bitmap){super.setImageBitmap(bitmap);placeholder.setVisibility(bitmap==null?View.VISIBLE:View.GONE);if(post!=null){post.coverHasImage=bitmap!=null;post.update();}}};image.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));text=paragraph(a);frame.addView(text,new FrameLayout.LayoutParams(-1,-1));
  }
 }
 static final class Slides extends RecyclerView.Adapter<Slide> {
  final PostHolder post;Slides(PostHolder post){this.post=post;}
  @Override public int getItemCount(){return 1+Math.max(1,post.state.pages.size());}
  @Override public Slide onCreateViewHolder(ViewGroup parent,int type){return new Slide(post.a);}
  @Override public void onBindViewHolder(Slide slide,int position){State s=post.state;boolean cover=position==0;slide.post=post;slide.image.setTag(null);slide.image.setImageDrawable(null);slide.placeholder.setVisibility(cover?View.VISIBLE:View.GONE);slide.image.setVisibility(cover?View.VISIBLE:View.GONE);slide.text.setVisibility(cover?View.GONE:View.VISIBLE);
   if(cover){slide.placeholder.setGravity(Gravity.CENTER);slide.placeholder.setText(FeedScreens.plain(s.item.title));HindiTypography.apply(slide.placeholder,slide.placeholder.getText().toString(),Typeface.create(post.a.serif,Typeface.BOLD));slide.image.setContentDescription(FeedScreens.plain(s.item.title)+" · Article image");slide.image.setScaleType(ImageView.ScaleType.CENTER_CROP);if(!s.lead.isEmpty())FeedArtwork.bind(post.a,slide.image,s.lead);else post.title.setVisibility(View.GONE);}
   else{HindiTypography.apply(slide.text,s.body,post.a.serif);slide.text.setText(position-1<s.pages.size()?StoryPages.spaced(s.pages.get(position-1).text,post.a.dp(16),post.a.dp(3),1.16f):s.loading?"Loading the story…":"This feed has no article text. Use Open website to read the story.");slide.text.setTextIsSelectable(true);}
  }
  @Override public void onViewRecycled(Slide slide){slide.post=null;slide.image.setTag(null);slide.image.setImageDrawable(null);}
 }
}
