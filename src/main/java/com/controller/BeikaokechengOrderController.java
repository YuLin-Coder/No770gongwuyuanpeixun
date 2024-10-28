package com.controller;


import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
import java.util.*;

import com.entity.*;
import org.springframework.beans.BeanUtils;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import com.service.TokenService;
import com.utils.StringUtil;
import java.lang.reflect.InvocationTargetException;

import com.service.DictionaryService;
import org.apache.commons.lang3.StringUtils;
import com.annotation.IgnoreAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;

import com.service.BeikaokechengOrderService;
import com.entity.view.BeikaokechengOrderView;
import com.service.BeikaokechengService;
import com.service.YonghuService;
import com.utils.PageUtils;
import com.utils.R;

/**
 * 备考课程预定
 * 后端接口
 * @author
 * @email
 * @date 2021-04-07
*/
@RestController
@Controller
@RequestMapping("/beikaokechengOrder")
public class BeikaokechengOrderController {
    private static final Logger logger = LoggerFactory.getLogger(BeikaokechengOrderController.class);

    @Autowired
    private BeikaokechengOrderService beikaokechengOrderService;

    @Autowired
    private YonghuService yonghuService;

    @Autowired
    private TokenService tokenService;
    @Autowired
    private DictionaryService dictionaryService;


    //级联表service
    @Autowired
    private BeikaokechengService beikaokechengService;


    /**
    * 后端列表
    */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isNotEmpty(role) && "考生".equals(role)){
            params.put("yonghuId",request.getSession().getAttribute("userId"));
        }
        if(StringUtil.isNotEmpty(role) && "公务员".equals(role)){
            params.put("gongwuyuanId",request.getSession().getAttribute("userId"));
        }
        params.put("orderBy","id");
        PageUtils page = beikaokechengOrderService.queryPage(params);

        //字典表数据转换
        List<BeikaokechengOrderView> list =(List<BeikaokechengOrderView>)page.getList();
        for(BeikaokechengOrderView c:list){
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(c);
        }
        return R.ok().put("data", page);
    }

    /**
    * 后端详情
    */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        BeikaokechengOrderEntity beikaokechengOrder = beikaokechengOrderService.selectById(id);
        if(beikaokechengOrder !=null){
            //entity转view
            BeikaokechengOrderView view = new BeikaokechengOrderView();
            BeanUtils.copyProperties( beikaokechengOrder , view );//把实体数据重构到view中

            //级联表
            BeikaokechengEntity beikaokecheng = beikaokechengService.selectById(beikaokechengOrder.getBeikaokechengId());
            if(beikaokecheng != null){
                BeanUtils.copyProperties( beikaokecheng , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                view.setBeikaokechengId(beikaokecheng.getId());
            }
            //级联表
            YonghuEntity yonghu = yonghuService.selectById(beikaokechengOrder.getYonghuId());
            if(yonghu != null){
                BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                view.setYonghuId(yonghu.getId());
            }
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(view);
            return R.ok().put("data", view);
        }else {
            return R.error(511,"查不到数据");
        }

    }

    /**
    * 后端保存
    */
    @RequestMapping("/save")
    public R save(@RequestBody BeikaokechengOrderEntity beikaokechengOrder, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,beikaokechengOrder:{}",this.getClass().getName(),beikaokechengOrder.toString());
        if(!request.getSession().getAttribute("role").equals("考生")){
            return R.error("只有考生能预定这个课程哦");
        }
        BeikaokechengEntity beikaokecheng = beikaokechengService.selectById(beikaokechengOrder.getBeikaokechengId());
        if(beikaokecheng == null){
            return R.error();
        }
        YonghuEntity user = yonghuService.selectById((Integer) request.getSession().getAttribute("userId"));
        if(user == null){
            return R.error();
        }
        if(user.getNewMoney() < beikaokecheng.getBeikaokechengMoney()){
            return R.error("余额不足请充值后在预定");
        }
        Wrapper<BeikaokechengOrderEntity> queryWrapper = new EntityWrapper<BeikaokechengOrderEntity>()
                .eq("yonghu_id", beikaokechengOrder.getYonghuId())
                .eq("beikaokecheng_id",beikaokechengOrder.getBeikaokechengId())
                ;
        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        BeikaokechengOrderEntity beikaokechengOrderEntity = beikaokechengOrderService.selectOne(queryWrapper);
        if(beikaokechengOrderEntity != null){
            return R.error("你已经预定过了");
        }
        beikaokechengOrder.setInsertTime(new Date());
        beikaokechengOrder.setCreateTime(new Date());
        boolean insert = beikaokechengOrderService.insert(beikaokechengOrder);
        if(insert){
            user.setNewMoney(user.getNewMoney() - beikaokecheng.getBeikaokechengMoney());
            boolean b = yonghuService.updateById(user);
            if(b){
                return R.ok();
            }
        }
        return R.error();
    }


    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody BeikaokechengOrderEntity beikaokechengOrder, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,beikaokechengOrder:{}",this.getClass().getName(),beikaokechengOrder.toString());
        beikaokechengOrderService.updateById(beikaokechengOrder);//根据id更新
        return R.ok();
    }


    /**
    * 删除
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        beikaokechengOrderService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }



    /**
    * 前端列表
    */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(!role.equals("公务员")){
            if(StringUtil.isNotEmpty(role) && "考生".equals(role)) {
                params.put("yonghuId", request.getSession().getAttribute("userId"));
            }
            // 没有指定排序字段就默认id倒序
            if(StringUtil.isEmpty(String.valueOf(params.get("orderBy")))){
                params.put("orderBy","id");
            }
            PageUtils page = beikaokechengOrderService.queryPage(params);

            //字典表数据转换
            List<BeikaokechengOrderView> list =(List<BeikaokechengOrderView>)page.getList();
            for(BeikaokechengOrderView c:list){
                //修改对应字典表字段
                dictionaryService.dictionaryConvert(c);
            }
            return R.ok().put("data", page);
        }
            return R.error("没有数据");
    }

    /**
    * 前端详情
    */
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        logger.debug("detail方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        BeikaokechengOrderEntity beikaokechengOrder = beikaokechengOrderService.selectById(id);
            if(beikaokechengOrder !=null){
                //entity转view
        BeikaokechengOrderView view = new BeikaokechengOrderView();
                BeanUtils.copyProperties( beikaokechengOrder , view );//把实体数据重构到view中

                //级联表
                    BeikaokechengEntity beikaokecheng = beikaokechengService.selectById(beikaokechengOrder.getBeikaokechengId());
                if(beikaokecheng != null){
                    BeanUtils.copyProperties( beikaokecheng , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setBeikaokechengId(beikaokecheng.getId());
                }
                //级联表
                    YonghuEntity yonghu = yonghuService.selectById(beikaokechengOrder.getYonghuId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setYonghuId(yonghu.getId());
                }
                //修改对应字典表字段
                dictionaryService.dictionaryConvert(view);
                return R.ok().put("data", view);
            }else {
                return R.error(511,"查不到数据");
            }
    }


    /**
    * 前端保存
    */
    @RequestMapping("/add")
    public R add(@RequestBody BeikaokechengOrderEntity beikaokechengOrder, HttpServletRequest request){
        logger.debug("add方法:,,Controller:{},,beikaokechengOrder:{}",this.getClass().getName(),beikaokechengOrder.toString());
        if(!request.getSession().getAttribute("role").equals("考生")){
            return R.error("只有考生能预定这个课程哦");
        }
        BeikaokechengEntity beikaokecheng = beikaokechengService.selectById(beikaokechengOrder.getBeikaokechengId());
        if(beikaokecheng == null){
            return R.error();
        }
        YonghuEntity user = yonghuService.selectById((Integer) request.getSession().getAttribute("userId"));
        if(user == null){
            return R.error();
        }
        if(user.getNewMoney() < beikaokecheng.getBeikaokechengMoney()){
            return R.error("余额不足请充值后在预定");
        }
        Wrapper<BeikaokechengOrderEntity> queryWrapper = new EntityWrapper<BeikaokechengOrderEntity>()
                .eq("yonghu_id", beikaokechengOrder.getYonghuId())
                .eq("beikaokecheng_id",beikaokechengOrder.getBeikaokechengId())
                ;
        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        BeikaokechengOrderEntity beikaokechengOrderEntity = beikaokechengOrderService.selectOne(queryWrapper);
        if(beikaokechengOrderEntity != null){
            return R.error("你已经预定过了");
        }
        beikaokechengOrder.setInsertTime(new Date());
        beikaokechengOrder.setCreateTime(new Date());
        boolean insert = beikaokechengOrderService.insert(beikaokechengOrder);
        if(insert){
            user.setNewMoney(user.getNewMoney() - beikaokecheng.getBeikaokechengMoney());
            boolean b = yonghuService.updateById(user);
            if(b){
                return R.ok();
            }
        }
        return R.error();
    }


}
