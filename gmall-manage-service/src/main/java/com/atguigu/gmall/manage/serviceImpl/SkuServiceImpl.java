package com.atguigu.gmall.manage.serviceImpl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.manage.mapper.PmsSkuAttrValueMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuImageMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuInfoMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuSaleAttrValueMapper;
import com.atguigu.gmall.service.SkuService;
import com.atguigu.gmall.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class SkuServiceImpl implements SkuService {

    @Autowired
    RedisUtil redisUtil;
    @Autowired
    PmsSkuInfoMapper pmsSkuInfoMapper;

    @Autowired
    PmsSkuSaleAttrValueMapper pmsSkuSaleAttrValueMapper;

    @Autowired
    PmsSkuAttrValueMapper pmsSkuAttrValueMapper;

    @Autowired
    PmsSkuImageMapper pmsSkuImageMapper;

    @Override
    public void saveSkuInfo(PmsSkuInfo pmsSkuInfo) {

        //保存sku信息
        pmsSkuInfoMapper.insert(pmsSkuInfo);

        //生成主键
        String skuInfoId = pmsSkuInfo.getId();

        //获取销售属性值集合
        List<PmsSkuSaleAttrValue> skuSaleAttrValueList = pmsSkuInfo.getSkuSaleAttrValueList();

        for (PmsSkuSaleAttrValue pmsSkuSaleAttrValue : skuSaleAttrValueList) {

            pmsSkuSaleAttrValue.setSkuId(skuInfoId);

            pmsSkuSaleAttrValueMapper.insertSelective(pmsSkuSaleAttrValue);

        }

        List<PmsSkuAttrValue> skuAttrValueList = pmsSkuInfo.getSkuAttrValueList();

        for (PmsSkuAttrValue pmsSkuAttrValue : skuAttrValueList) {

            pmsSkuAttrValue.setSkuId(skuInfoId);

            pmsSkuAttrValueMapper.insertSelective(pmsSkuAttrValue);

        }

        List<PmsSkuImage> skuImageList = pmsSkuInfo.getSkuImageList();

        for (PmsSkuImage pmsSkuImage : skuImageList) {

            pmsSkuImage.setSkuId(skuInfoId);

            pmsSkuImageMapper.insertSelective(pmsSkuImage);

        }
    }

    public PmsSkuInfo itemFromDb(String skuId) {

        PmsSkuInfo skuInfo = new PmsSkuInfo();

        skuInfo.setId(skuId);

        PmsSkuInfo pmsSkuInfo = pmsSkuInfoMapper.selectOne(skuInfo);

        PmsSkuImage pmsSkuImage = new PmsSkuImage();

        pmsSkuImage.setSkuId(skuId);

        List<PmsSkuImage> pmsSkuImages = pmsSkuImageMapper.select(pmsSkuImage);

        pmsSkuInfo.setSkuImageList(pmsSkuImages);

        return pmsSkuInfo;
    }
    @Override
    public PmsSkuInfo item(String skuId) {

        PmsSkuInfo skuInfo = null;
        Jedis jedis = null;
        try {
            jedis = redisUtil.getJedis();

            String skuInfoStr = jedis.get("Sku:" + skuId + ":info");

            if(StringUtils.isNotBlank(skuInfoStr)){

                //有sku缓存
                skuInfo = JSON.parseObject(skuInfoStr,PmsSkuInfo.class);

            }else {

                String lockId = UUID.randomUUID().toString();
                //获取锁
                String OK = jedis.set("Sku:" + skuId + ":lock", lockId, "nx", "px", 10000);

                if(StringUtils.isNotBlank(OK)&&OK.equals("OK")){

                    skuInfo = itemFromDb(skuId);

                    //把数据存放入Redis中
                    jedis.set("Sku:" + skuId + ":info",JSON.toJSONString(skuInfo));

                    //释放锁之前要判断拿到的是不是当前线程的锁
                    //如果删除命令在传输过程中，锁突然失效了，那应该怎么办
                    //释放锁
                    //用lua脚本把判断和删除命令合为一条命令，当发现锁就删除
                    String script ="if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    Object eval = jedis.eval(script, Collections.singletonList("sku:" + skuId + ":lock"), Collections.singletonList(lockId));
                }else {

                    Thread.sleep(3000);
                    //自旋
                    return item(skuId);
                }

            }

        }catch (Exception e){

            e.printStackTrace();
        }finally {
            jedis.close();
        }

        return skuInfo;
    }

    @Override
    public List<PmsSkuInfo> getSkuSaleAttrValueListBySpu(String spuId) {

        List<PmsSkuInfo> pmsSkuInfos = pmsSkuSaleAttrValueMapper.selectSkuSaleAttrValueListBySpu(spuId);

        return pmsSkuInfos;
    }

    @Override
    public List<PmsSkuInfo> getAllSku() {

        List<PmsSkuInfo> pmsSkuInfos = pmsSkuInfoMapper.selectAll();

        for (PmsSkuInfo pmsSkuInfo : pmsSkuInfos) {

            String skuInfoId = pmsSkuInfo.getId();

            PmsSkuAttrValue pmsSkuAttrValue = new PmsSkuAttrValue();

            pmsSkuAttrValue.setSkuId(skuInfoId);

            List<PmsSkuAttrValue> pmsSkuAttrValues = pmsSkuAttrValueMapper.select(pmsSkuAttrValue);

            pmsSkuInfo.setSkuAttrValueList(pmsSkuAttrValues);

        }
        return pmsSkuInfos;
    }

    @Override
    public PmsSkuInfo getSkuById(String productSkuId) {

        PmsSkuInfo pmsSkuInfo = new PmsSkuInfo();

        pmsSkuInfo.setId(productSkuId);

        PmsSkuInfo pmsSkuInfo1 = pmsSkuInfoMapper.selectOne(pmsSkuInfo);

        return pmsSkuInfo1;
    }

    @Override
    public boolean checkPrice(OmsCartItem omsCartItem) {

        boolean b = false;

        PmsSkuInfo pmsSkuInfo = new PmsSkuInfo();

        pmsSkuInfo.setId(omsCartItem.getProductSkuId());

        PmsSkuInfo pmsSkuInfo1 = pmsSkuInfoMapper.selectOne(pmsSkuInfo);

        int i = pmsSkuInfo1.getPrice().compareTo(omsCartItem.getPrice());

        if(i == 0){

            b = true;
        }

        return b;

    }

}
