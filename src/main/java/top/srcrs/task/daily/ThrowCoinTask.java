package top.srcrs.task.daily;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.domain.Config;
import top.srcrs.domain.UserData;
import top.srcrs.util.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 进行视频投币
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public class ThrowCoinTask implements Task {
    /** 获取DATA对象 */
    private static final UserData USER_DATA = UserData.getInstance();
    Config config = Config.getInstance();

    @Override
    public void run() {
        try {
            /* 今天投币获得了多少经验 */
//            int reward = getReward();
            int reward = 0;
            /* 还剩多少个硬币 */
            int num2 = USER_DATA.getMoney().intValue();
            /* 配置类中设置投币数 */
            int num3 = config.getCoin();
            /* 计算今天需要投 num1 个硬币
               当今日已经投过 num3 个硬币将不再进行投币
               否则则应该投 (num3-num1) 个硬币
            */
            int num1 = (num3*10 - reward) / 10;
            /* 避免设置投币数为负数异常 */
            num3 = Math.max(num3,0);
            /* 实际需要投 num个硬币 */
            int num = Math.min(num3,Math.min(num1,num2));
            log.info("【投幣計算】: 目前投幣數: " + num2
                    + " ,自定義投幣數: " + num3
                    + " ,今日已投幣: " + reward/10
                    + " ,還需投幣: "+num1
                    + " ,實際投幣: "+num);
            if(num == 0){
                log.info("【投幣】: 目前無需執行投幣操作❌");
            }
            /* 获取视频信息，优先级为:
                     自定义配置 up 主发布的最新视频(前 30 条) >
                     当前用户动态列表投稿视频(已关注 up 主视频投稿都会在动态列表出现)(前 20 条) >
                     随机从分区热门视频中获取(前六条)
            */
            List<String> videoAid = new ArrayList<>();
            /* 获取自定义配置中 up 主投稿的30条最新视频 */
            if(config.getUpList() == null && num > 0){
                log.info("【優先投幣up】: 未設定優先投幣up主");
            } else{
                if(num - videoAid.size() > 0){
                    for(String up : config.getUpList()){
                        videoAid.addAll(spaceSearch(up,num - videoAid.size()));
                        log.info("【優先投幣up {} 】: 成功獲取到: {} 個影片", up, videoAid.size());
                    }
                }
            }
            /* 获取当前用户最新的20条动态投稿视频列表 */
            if(num - videoAid.size() > 0){
                videoAid.addAll(dynamicNew(num - videoAid.size()));
                log.info("【用戶動態列表】: 成功獲取到: {} 個影片", videoAid.size());
            }
            /* 获取分区视频 */
            if(num - videoAid.size() > 0){
                videoAid.addAll(getRegions("6", "1",num - videoAid.size()));
                log.info("【分區熱門影片】: 成功獲取到: {} 個影片", videoAid.size());
            }
            /* 给每个视频投 1 个币 */
            /* 在配置文件中读取是否为投币视频点赞 */
            for (int i = 0; i < num; i++) {
                /* 视频的aid */
                String aid = videoAid.get(i);
                JSONObject json = throwCoin(aid, "1", config.getSelectLike());
                /* 输出的日志消息 */
                String msg ;
                if ("0".equals(json.getString("code"))) {
                    msg = "硬幣-1✔";
                } else {
                    msg = json.getString("message") + "❌";
                }
                log.info("【投幣】: 給影片 - av{} - {}", aid, msg);
                /* 投完币等待1-2秒 */
                Thread.sleep(new Random().nextInt(1000)+1000);
            }
        } catch (Exception e) {
            log.info("💔投幣異常 : ", e);
        }
    }

    /**
     * 给视频投币
     * @param aid         视频 aid 号
     * @param num         投币数量
     * @param selectLike 是否点赞
     * @return JSONObject 返回投币的结果
     * @author srcrs
     * @Time 2020-10-13
     */
    public JSONObject throwCoin(String aid, String num, String selectLike) {
        JSONObject pJson = new JSONObject();
        pJson.put("aid", aid);
        pJson.put("multiply", num);
        pJson.put("select_like", selectLike);
        pJson.put("cross_domain", "true");
        pJson.put("csrf", USER_DATA.getBiliJct());
        return Request.post("https://api.bilibili.com/x/web-interface/coin/add", pJson);
    }

    /**
     * 获取今天投币所得经验
     * @return JSONObject
     * @author srcrs
     * @Time 2020-10-13
     */
    public Integer getReward() {
        JSONObject jsonObject = Request.get("https://account.bilibili.com/home/reward");
        return Integer.parseInt(jsonObject.getJSONObject("data").getString("coins_av"));
    }

    /**
     * 获取B站分区视频信息
     * @param ps  获取视频的数量
     * @param rid 分区号
     * @return JSONArray
     * @author srcrs
     * @Time 2020-10-13
     */
    public List<String> getRegions(String ps, String rid, int num) {
        JSONObject pJson = new JSONObject();
        pJson.put("ps", ps);
        pJson.put("rid", rid);
        JSONObject jsonObject = Request.get("https://api.bilibili.com/x/web-interface/dynamic/region", pJson);
        JSONArray archives = jsonObject.getJSONObject("data").getJSONArray("archives");
        List<String> videoAid = new ArrayList<>();
        for (Object object : archives) {
            JSONObject archive = (JSONObject) object;
            String aid = archive.getString("aid");
            String mid = archive.getJSONObject("owner").getString("mid");
            if (isThrowCoins(aid, mid)) {
                videoAid.add(aid);
            }
            if (videoAid.size() >= num) {
                break;
            }
        }
        return videoAid;
    }

    /**
     * 获取当前用户最新的20条动态投稿视频列表
     * @return List<String> 返回将要投币视频的aid
     * @author srcrs
     * @Time 2020-11-17
     */
    private List<String> dynamicNew(int num){
        JSONObject pJson = new JSONObject();
        pJson.put("uid", USER_DATA.getMid());
        pJson.put("type_list", 8);
        JSONObject dynamic = Request.get("https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/dynamic_new", pJson);
        List<String> videoAid = new ArrayList<>();
        String success = "0";
        String key = "code";
        if(success.equals(dynamic.getString(key))){
            JSONArray cards = dynamic.getJSONObject("data").getJSONArray("cards");
            // 没有任何动态，则不会有 cards 数组
            if(cards==null){
                return new ArrayList<>();
            }
            for(Object object : cards){
                JSONObject card = (JSONObject) object;
                String aid = card.getJSONObject("desc").getString("rid");
                String mid = card.getJSONObject("desc").getString("uid");
                if (isThrowCoins(aid, mid)) {
                    videoAid.add(aid);
                }
                if(videoAid.size()>=num){
                    break;
                }
            }
        }
        return videoAid;
    }

    /**
     * 判断视频是否被投币
     * @param aid 视频的 aid 号
     * @param mid 用户的 mid 号
     * @return boolean 是否投币过
     * @author srcrs
     * @Time 2020-11-17
     */
    private boolean isThrowCoins(String aid, String mid){
        // 自己的视频跳过
        if((USER_DATA.getMid().equals(mid))){
            return false;
        }
        JSONObject pJson = new JSONObject();
        pJson.put("aid", aid);
        JSONObject object = Request.get("https://api.bilibili.com/x/web-interface/archive/coins", pJson);
        int multiply = object.getJSONObject("data").getIntValue("multiply");
        return multiply == 0;
    }

    /**
     * 获取 up 主的最新30条视频投稿信息
     * 从中挑选从未投币的视频进行投币
     * @param uid up 主的 uid 号
     * @return List<String> 返回未投币的5个视频
     * @param num 需要投币的数量
     * @author srcrs
     * @Time 2020-11-17
     */
    private List<String> spaceSearch(String uid,int num){
        JSONObject pJson = new JSONObject();
        pJson.put("mid", uid);
        JSONObject spaceVideo = Request.get("https://api.bilibili.com/x/space/arc/search", pJson);
        List<String> videoAid = new ArrayList<>();
        String success = "0";
        String key = "code";
        if(success.equals(spaceVideo.getString(key))){
            JSONArray vList = spaceVideo.getJSONObject("data")
                    .getJSONObject("list")
                    .getJSONArray("vlist");
            for(Object object : vList){
                JSONObject video = (JSONObject) object;
                String aid = video.getString("aid");
                String mid = video.getString("mid");
                if (isThrowCoins(aid, mid)) {
                    videoAid.add(aid);
                }
                if(videoAid.size()>=num){
                    break;
                }
            }
        }
        return videoAid;
    }
}
