<template>
  <div>
    <el-menu mode="horizontal" :default-active="$route.path" router>
      <el-menu-item index="/buyer/goods">买家端 - 商品页</el-menu-item>
      <el-menu-item index="/seller/login">卖家端 - 登录页</el-menu-item>
    </el-menu>
    <router-view />
  </div>
</template>

<style>
body { margin: 0; }
</style>