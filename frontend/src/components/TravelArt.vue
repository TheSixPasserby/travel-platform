<script setup lang="ts">
withDefaults(
  defineProps<{
    /** hero 用于首页大图，trail 用于登录/注册页侧栏插图。 */
    variant?: 'hero' | 'trail'
  }>(),
  { variant: 'hero' }
)
</script>

<template>
  <!-- 原创线描山水：层叠远山、松林与蜿蜒山径，不使用外部图片素材。 -->
  <svg
    v-if="variant === 'hero'"
    viewBox="0 0 880 620"
    role="img"
    aria-label="层叠远山与松林的旅行插画"
    class="h-full w-full"
  >
    <defs>
      <linearGradient id="art-sky" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#F3EFE4" />
        <stop offset="100%" stop-color="#FBF8F1" />
      </linearGradient>
      <linearGradient id="art-far" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#C9D8C6" />
        <stop offset="100%" stop-color="#DDE6D6" />
      </linearGradient>
      <linearGradient id="art-mid" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#9DBBA4" />
        <stop offset="100%" stop-color="#BCD0BA" />
      </linearGradient>
      <linearGradient id="art-near" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#2F6B52" />
        <stop offset="100%" stop-color="#145C45" />
      </linearGradient>
    </defs>

    <rect width="880" height="620" rx="28" fill="url(#art-sky)" />
    <circle cx="672" cy="132" r="58" fill="#E8B98C" opacity="0.85" />
    <circle cx="672" cy="132" r="86" fill="#E8B98C" opacity="0.18" />

    <path d="M0 372 L118 268 L186 322 L292 208 L402 336 L494 258 L580 330 L688 236 L790 320 L880 262 L880 620 L0 620 Z" fill="url(#art-far)" opacity="0.9" />
    <path d="M0 442 L96 372 L168 414 L268 330 L352 400 L452 344 L548 410 L648 340 L734 396 L820 348 L880 392 L880 620 L0 620 Z" fill="url(#art-mid)" opacity="0.92" />

    <path d="M0 546 C132 508 224 470 322 486 C424 502 486 552 596 540 C700 528 792 486 880 500 L880 620 L0 620 Z" fill="url(#art-near)" />
    <path d="M0 578 C150 546 256 520 358 534 C470 550 542 584 648 572 C744 562 812 540 880 548 L880 620 L0 620 Z" fill="#0D3F2F" opacity="0.9" />

    <g stroke="#F9F7F2" stroke-linecap="round" fill="none" opacity="0.7">
      <path d="M286 470 C334 452 372 462 400 492 C424 518 452 512 484 486" stroke-width="3" stroke-dasharray="10 12" />
    </g>

    <g fill="#0D3F2F">
      <path d="M132 470 l16 34 h-32 z" />
      <path d="M132 492 l18 38 h-36 z" />
      <path d="M760 452 l14 30 h-28 z" />
      <path d="M760 472 l16 34 h-32 z" />
      <path d="M700 486 l11 24 h-22 z" opacity="0.8" />
    </g>

    <g fill="none" stroke="#145C45" stroke-width="2" opacity="0.35">
      <path d="M64 300 q18 -26 36 0" />
      <path d="M104 328 q16 -22 32 0" />
      <path d="M612 372 q16 -22 32 0" />
    </g>
  </svg>

  <svg
    v-else
    viewBox="0 0 520 620"
    role="img"
    aria-label="山径与松树的旅行插画"
    class="h-full w-full"
  >
    <defs>
      <linearGradient id="trail-bg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="#145C45" />
        <stop offset="100%" stop-color="#0D3F2F" />
      </linearGradient>
    </defs>

    <rect width="520" height="620" rx="24" fill="url(#trail-bg)" />
    <circle cx="356" cy="128" r="46" fill="#E8B98C" opacity="0.9" />

    <path d="M0 402 L92 322 L158 366 L246 288 L330 360 L410 306 L520 384 L520 620 L0 620 Z" fill="#DDE6D6" opacity="0.22" />
    <path d="M0 470 C96 434 182 438 260 462 C346 488 428 470 520 436 L520 620 L0 620 Z" fill="#DDE6D6" opacity="0.16" />

    <path
      d="M196 620 C232 552 246 500 300 462 C352 426 386 398 404 356"
      fill="none"
      stroke="#F9F7F2"
      stroke-width="4"
      stroke-linecap="round"
      stroke-dasharray="14 16"
      opacity="0.85"
    />

    <g fill="#F9F7F2" opacity="0.92">
      <path d="M112 512 l20 42 h-40 z" />
      <path d="M112 540 l23 46 h-46 z" />
      <path d="M428 460 l15 32 h-30 z" />
      <path d="M428 484 l18 36 h-36 z" opacity="0.75" />
    </g>

    <g fill="none" stroke="#F9F7F2" stroke-width="2.5" stroke-linecap="round" opacity="0.5">
      <path d="M240 214 q16 -20 32 0" />
      <path d="M288 190 q14 -18 28 0" />
    </g>
  </svg>
</template>
