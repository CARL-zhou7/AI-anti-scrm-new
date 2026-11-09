<script>
export default {}
</script>

<style>
page { background: #e8f1f8; color: #102638; font: 28rpx/1.55 system-ui, sans-serif; }
view, text, image, input, textarea, button { box-sizing: border-box; }
button { margin: 0; padding: 18rpx 24rpx; border-radius: 14rpx; background: #102b3d; color: #fff; font-size: 28rpx; line-height: 1.4; }
button::after { border: 0; }
button[disabled] { opacity: .5; }
input, textarea { width: 100%; padding: 16rpx 20rpx; border: 1px solid #b9cbd8; border-radius: 12rpx; color: #102638; }
input { height: 74rpx; }
textarea { min-height: 168rpx; }
.page { padding: 24rpx 28rpx calc(38rpx + env(safe-area-inset-bottom)); }
.surface { position: relative; padding: 28rpx; background: #e8f1f8; color: #102638; border-radius: 28rpx 28rpx 0 0; }
.overlap { margin-top: -26rpx; }
.hero { position: relative; display: flex; align-items: flex-end; min-height: 320rpx; overflow: hidden; background: #0c2233; color: #fff; }
.hero-art { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-copy { position: relative; width: 100%; padding: 60rpx 32rpx 54rpx; background: linear-gradient(transparent, rgba(7,24,37,.94)); }
.title { display: block; font-size: 52rpx; line-height: 1.18; font-weight: 800; }
.heading { display: block; padding-left: 14rpx; border-left: 6rpx solid #e97541; font-size: 32rpx; line-height: 1.22; font-weight: 750; margin: 24rpx 0 20rpx; }
.eyebrow { display: inline-block; border-radius: 7rpx; padding: 4rpx 13rpx; background: #aa411f; color: white; font-size: 23rpx; font-weight: 700; }
.muted { color: #4e6576; font-size: 25rpx; }
.small { font-size: 23rpx; }
.block { display: block; }
.bold { font-weight: 750; }
.center { text-align: center; }
.error { display: block; padding: 16rpx; margin: 16rpx 0; background: #ffe9dc; color: #8f3219; border-radius: 12rpx; }
.row { display: flex; align-items: center; gap: 14rpx; }
.between { justify-content: space-between; }
.grow { flex: 1; min-width: 0; }
.stack > view + view, .actions > button + button { margin-top: 16rpx; }
.actions { margin-top: 24rpx; }
.ghost { background: transparent; color: #102638; border: 1px solid #aebfcd; }
.accent { background: linear-gradient(110deg, #ad461f, #d9662d); color: #fff; }
.link { padding: 12rpx 0; background: transparent; color: #235d86; font-size: 25rpx; }
.tile { padding: 20rpx; background: #dce9f2; border-radius: 14rpx; }
.divider { border-bottom: 1px solid #ccdce8; }
.avatar { width: 96rpx; height: 104rpx; background: #274150; border-radius: 12rpx; }
.empty { padding: 72rpx 18rpx 42rpx; text-align: center; }
.symbol { display: block; color: #bc5027; font-size: 82rpx; line-height: 1.2; margin-bottom: 20rpx; }
.foot { height: 112rpx; }
.tabs { display: flex; border-bottom: 1px solid #cbdce9; margin-bottom: 16rpx; }
.tab { flex: 1; text-align: center; padding: 12rpx 4rpx; font-weight: 700; color: #4e6576; }
.tab.active { border-bottom: 5rpx solid #e97541; color: #102638; }
</style>
