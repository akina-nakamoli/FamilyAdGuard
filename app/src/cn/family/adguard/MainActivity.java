package cn.family.adguard;
import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.net.VpnService;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout page; private TextView status;
    private final Handler handler=new Handler();
    private final Runnable refresh=new Runnable(){public void run(){if(status!=null)status.setText((GuardService.running?"● 防护已开启":"○ 防护未开启")+"\n累计拦截 "+getSharedPreferences("guard",0).getLong("count",0)+" 次 DNS 请求\n"+getSharedPreferences("guard",0).getString("error",""));handler.postDelayed(this,1500);}};
    public void onCreate(Bundle saved){super.onCreate(saved);home();}
    public void onResume(){super.onResume();handler.post(refresh);}
    public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void screen(String title){ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(1);page.setPadding(28,35,28,40);page.setBackgroundColor(0xfff4f7f5);scroll.addView(page);setContentView(scroll);text(title,30);status=null;}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff193c30);t.setPadding(0,14,0,14);page.addView(t);return t;}
    private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(22);b.setAllCaps(false);b.setMinHeight(76);page.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());}
    private void launch(Intent intent){try{startActivity(intent);}catch(Exception e){Toast.makeText(this,"此手机未提供该设置，请在系统设置中手动查找",Toast.LENGTH_LONG).show();}}
    private void home(){screen("安心广告管家");status=text("正在读取防护状态…",24);
        button("开启广告防护",()->new AlertDialog.Builder(this).setTitle("开启本地 DNS 防护").setMessage("将占用手机的 VPN 连接，与其他 VPN 互斥。仅 DNS 查询在手机上过滤，允许的域名查询发送到阿里公共 DNS（223.5.5.5，未加密）。不上传应用列表或聊天内容。\n\n只能拦截部分广告。使用加密 DNS、直接 IP 或与正文共用域名的广告可能无法过滤。网络异常时可停止防护。请勿开启系统的“阻止未使用 VPN 的连接”。").setPositiveButton("开启",(d,w)->prepareVpn()).setNegativeButton("取消",null).show());
        button("停止防护",()->{stopService(new Intent(this,GuardService.class));});
        button("查找乱弹广告的软件",()->apps());
        button("更新通用广告规则",()->universal());
        button("管理拦截规则",()->rules());
        button("管理放行白名单",()->allowlist());
        button("广告漏拦诊断",()->diagnostics());
        button("使用说明",()->new AlertDialog.Builder(this).setTitle("给家人的说明").setMessage("1. 开启防护后，首次需要同意安卓的 VPN 提示。\n2. 弹窗出现时先不要点广告，返回这里查看近期应用。\n3. 进入对应应用的系统设置，关闭通知和悬浮窗，或确认后卸载。权限申请只是线索，不代表有害。\n4. 本软件不自动点击广告、不清除照片，也不会自动卸载应用。\n5. 某软件无法正常使用时，先停止防护。手机重启后需手动开启；系统省电可能结束防护。\n6. 拦截次数是 DNS 请求数，不等于广告数量。\n7. 本机不保存域名访问记录，仅保存规则、计数和最近错误。VPN 授权可在系统设置中撤销。").setPositiveButton("知道了",null).show());
        text("帮助减少广告，不保证全部拦截。\n不申请无障碍、悬浮窗或设备管理权限。",18);
    }
    private void prepareVpn(){Intent permission=VpnService.prepare(this);if(permission!=null)startActivityForResult(permission,10);else startGuard();}
    protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==10&&result==RESULT_OK)startGuard();}
    private void startGuard(){getSharedPreferences("guard",0).edit().remove("error").apply();if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},11);startForegroundService(new Intent(this,GuardService.class));}
    private void apps(){screen("排查弹窗来源");button("返回首页",()->home());
        text("先看弹窗出现前后使用的应用。下方“申请悬浮窗权限”仅代表声明过该权限，无法判断当前是否开启。",18);
        button("允许查看近期使用记录",()->launch(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        button("刷新应用列表",()->apps());
        button("查看系统悬浮窗权限",()->launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)));
        UsageStatsManager us=getSystemService(UsageStatsManager.class);Map<String,UsageStats> recent=us.queryAndAggregateUsageStats(System.currentTimeMillis()-86400000L,System.currentTimeMillis());
        if(recent.isEmpty())text("暂时没有近期记录。可先授予使用情况访问，再回来刷新；也可直接检查下面的应用。",18);
        PackageManager pm=getPackageManager();List<ResolveInfo> launchers=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        Map<String,ApplicationInfo> unique=new HashMap<>();for(ResolveInfo ri:launchers)if(!getPackageName().equals(ri.activityInfo.packageName))unique.put(ri.activityInfo.packageName,ri.activityInfo.applicationInfo);
        List<ApplicationInfo> list=new ArrayList<>(unique.values());list.sort((a,b)->Long.compare(last(recent,b.packageName),last(recent,a.packageName)));
        for(ApplicationInfo ai:list){String pkg=ai.packageName;boolean overlay=false;try{String[] permissions=pm.getPackageInfo(pkg,PackageManager.GET_PERMISSIONS).requestedPermissions;if(permissions!=null)for(String p:permissions)if("android.permission.SYSTEM_ALERT_WINDOW".equals(p))overlay=true;}catch(Exception ignored){}
            long used=last(recent,pkg);String details=(used>0?"最近使用："+android.text.format.DateFormat.format("MM-dd HH:mm",used):"近一天暂无使用记录")+(overlay?"\n申请过悬浮窗权限":"");
            text(ai.loadLabel(pm)+"\n"+pkg+"\n"+details,19);
            button("查看设置 / 关闭通知 / 卸载",()->launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg))));
        }
        text("此处只显示可从桌面启动的应用。隐藏应用可能未列出，可到系统的全部应用列表继续检查。",18);
        button("打开系统全部应用",()->launch(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
    }
    private long last(Map<String,UsageStats> map,String pkg){UsageStats s=map.get(pkg);return s==null?0:s.getLastTimeUsed();}
    private void universal(){screen("通用广告规则");button("返回首页",()->home());
        final TextView info=text("正在读取规则…",20);
        new Thread(()->{try{Rules r=RuleStore.get(getApplicationContext());runOnUiThread(()->info.setText("anti-AD 通用规则："+r.size()+" 条\n规则版本："+r.version));}catch(Exception e){runOnUiThread(()->info.setText("规则读取失败，请重新安装"));}}).start();
        text("安装包已内置规则，无需先更新即可防护。规则用于所有软件经过本 VPN 的 DNS 请求。更新从 GitHub 下载，失败尝试 jsDelivr 镜像，均不可达时保留原规则。更多规则也可能造成误拦，可添加放行域名或暂停防护。",18);
        button("立即联网更新",()->{
            info.setText("正在下载，请稍候…");
            new Thread(()->{try{int count=RuleStore.update(getApplicationContext());runOnUiThread(()->info.setText("更新成功："+count+" 条，立即生效"));}catch(Exception e){runOnUiThread(()->info.setText("更新失败，保留原规则。可稍后重试。"));}}).start();
        });
        button("恢复安装包内置规则",()->new AlertDialog.Builder(this).setTitle("恢复内置规则？").setMessage("移除下载的规则版本，保留自定义拦截和白名单。").setPositiveButton("恢复",(d,w)->new Thread(()->{try{RuleStore.reset(getApplicationContext());runOnUiThread(()->info.setText("已恢复内置规则，立即生效"));}catch(Exception e){runOnUiThread(()->info.setText("恢复失败"));}}).start()).setNegativeButton("取消",null).show());
        button("查看规则来源",()->launch(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/privacy-protection-tools/anti-AD"))));
        button("查看开源许可证",()->{try{java.io.InputStream in=getAssets().open("ANTI-AD-LICENSE.txt");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);in.close();new AlertDialog.Builder(this).setTitle("anti-AD · MIT License").setMessage(out.toString("UTF-8")).setPositiveButton("关闭",null).show();}catch(Exception e){Toast.makeText(this,"许可证读取失败",Toast.LENGTH_SHORT).show();}});
    }
    private void allowlist(){screen("放行白名单");button("返回首页",()->home());text("每行一个可信域名。域名及其子域名将优先放行，即使命中通用规则。不要填过于宽泛的域名；添加后可能重新出现广告。",18);
        EditText edit=new EditText(this);edit.setTextSize(20);edit.setMinLines(5);edit.setText(getSharedPreferences("guard",0).getString("allow",""));page.addView(edit);
        button("保存白名单",()->{String value=edit.getText().toString().trim().toLowerCase(Locale.ROOT);for(String s:value.split("\\s+"))if(!s.isEmpty()&&(!s.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")||s.length()>253)){Toast.makeText(this,"请填写域名，不要填完整网址",Toast.LENGTH_SHORT).show();return;}getSharedPreferences("guard",0).edit().putString("allow",value).apply();Toast.makeText(this,"已保存，立即生效",Toast.LENGTH_SHORT).show();});
    }
    private void diagnostics(){screen("广告漏拦诊断");button("返回首页",()->home());
        text("诊断期间会临时显示经过本 VPN 的域名，可能涉及浏览隐私。仅保留手机内存中最近 100 条，不上传、不写文件，3 分钟后到期清除。无法判断域名属于哪个应用，也看不到绕过 DNS 的请求。",18);
        text(Trace.active()?"临时诊断进行中":"临时诊断未开启",22);
        button("开始 3 分钟临时诊断",()->new AlertDialog.Builder(this).setTitle("临时记录域名？").setMessage("请先关闭其他应用，再开始诊断，打开阅坊或随手记复现一次广告，然后回来刷新。截图前请检查并遮住不想分享的域名。").setPositiveButton("开始",(d,w)->{Trace.enable();diagnostics();}).setNegativeButton("取消",null).show());
        button("刷新诊断结果",()->diagnostics());button("结束并清除记录",()->{Trace.disable();diagnostics();});
        for(String line:Trace.snapshot())text(line,18);
    }
    private void rules(){screen("额外拦截规则");button("返回首页",()->home());text("通用规则已自动启用，可在首页查看和更新。这里每行可额外添加一个域名，也会拦截其子域名。填错可能影响正常使用；删除后保存即可恢复。",18);
        EditText edit=new EditText(this);edit.setTextSize(20);edit.setMinLines(5);edit.setGravity(Gravity.TOP);edit.setHint("例如：ads.example.com");edit.setText(getSharedPreferences("guard",0).getString("rules",""));page.addView(edit);
        button("保存额外规则",()->{String value=edit.getText().toString().trim().toLowerCase(Locale.ROOT);for(String rule:value.split("\\s+"))if(!rule.isEmpty()&&(!rule.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")||rule.length()>253)){Toast.makeText(this,"请只填完整域名，不要填网址、路径或通配符",Toast.LENGTH_LONG).show();return;}getSharedPreferences("guard",0).edit().putString("rules",value).apply();Toast.makeText(this,"已保存，立即生效",Toast.LENGTH_SHORT).show();});
        text("内置规则：\n"+String.join("\n",GuardService.RULES),16);
    }
}
