<script setup lang="ts">
import TravelArt from '@/components/TravelArt.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const sections = [
  {
    title: '沿着山径慢慢走',
    body: '把路线、落脚点和当天的天气写进手记，回看时仍能闻到松脂的味道。'
  },
  {
    title: '收藏值得再去的地方',
    body: '一处溪谷、一间老茶铺、一个能看日出的坡地，都可以单独留下一条记录。'
  },
  {
    title: '为下一程排好每一天',
    body: '把想去的地方按天排开，留出空白，让行程跟着天气和自己调整。'
  }
]
</script>

<template>
  <section class="app-container grid items-center gap-10 py-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)] lg:py-16">
    <div>
      <p class="text-xs tracking-[0.35em] text-ember">TRAVEL NOTES</p>
      <h1 class="mt-4 font-serif text-4xl leading-tight text-pine-deep sm:text-5xl">
        把走过的山路，<br class="hidden sm:block" />写成给自己的手记
      </h1>
      <p class="mt-5 max-w-xl text-base leading-relaxed text-ink-soft">
        山水间是一个安静的旅行手记本：记录攻略、收藏目的地、排出每日行程。
        没有冗余的推送，只留下你真正想再看一眼的地方。
      </p>

      <div class="mt-8 flex flex-wrap items-center gap-3">
        <RouterLink to="/guides" class="btn-primary">翻开旅行手记 <span aria-hidden="true">↗</span></RouterLink>
        <RouterLink v-if="!auth.isAuthenticated" to="/register" class="btn-ghost">
          开始记录
        </RouterLink>
        <RouterLink v-else to="/me/guides" class="btn-ghost">我的攻略</RouterLink>
        <RouterLink
          v-if="!auth.isAuthenticated"
          to="/login"
          class="btn-ghost"
        >
          已有账号，去登录
        </RouterLink>
      </div>

      <dl class="mt-10 grid gap-4 sm:grid-cols-2">
        <div v-for="item in sections.slice(0, 2)" :key="item.title" class="surface-card p-5">
          <dt class="font-serif text-lg text-pine-deep">{{ item.title }}</dt>
          <dd class="mt-2 text-sm leading-relaxed text-ink-soft">{{ item.body }}</dd>
        </div>
      </dl>
    </div>

    <div class="surface-card relative overflow-hidden p-3">
      <TravelArt variant="hero" class="aspect-[4/3] w-full" />
    </div>
  </section>

  <section class="app-container pb-12">
    <div class="surface-card p-6 sm:p-8">
      <h2 class="font-serif text-2xl text-pine-deep">这三件事，先做好</h2>
      <div class="mt-6 grid gap-6 sm:grid-cols-3">
        <article v-for="(item, index) in sections" :key="item.title">
          <span class="font-serif text-2xl text-ember">0{{ index + 1 }}</span>
          <h3 class="mt-2 text-base font-semibold text-ink">{{ item.title }}</h3>
          <p class="mt-1.5 text-sm leading-relaxed text-ink-soft">{{ item.body }}</p>
        </article>
      </div>
    </div>
  </section>
</template>
