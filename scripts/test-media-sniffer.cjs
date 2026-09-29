// Real embedded JavaScript, synthetic DOM/Web APIs. No dependency install or network.
// Run from repository root: node --test scripts/test-media-sniffer.cjs
const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const kotlin = fs.readFileSync('app/src/main/java/com/salvia/salviabrowxer/ui/utils/MediaSniffer.kt', 'utf8');
const rules = fs.readFileSync('core/model/src/main/java/com/salvia/salviabrowxer/core/model/MediaUrlRules.kt', 'utf8');
function set(name) {
  const body = rules.match(new RegExp(`val ${name}[^=]*= setOf\\(([\\s\\S]*?)\\)`))[1];
  return Array.from(body.matchAll(/"([^"]+)"/g), m => m[1]);
}
const media = [...set('VIDEO_EXTENSIONS'), ...set('AUDIO_EXTENSIONS'), ...set('PLAYLIST_EXTENSIONS')].join('|');
const script = kotlin.split('private const val SNIFFER_TEMPLATE = """')[1].split('"""')[0]
  .replace('__MEDIA_EXT__', media)
  .replace('__NON_MEDIA_EXT__', set('NON_MEDIA_EXTENSIONS').join('|'))
  .replace('__SEGMENT_EXT__', set('SEGMENT_EXTENSIONS').join('|'));
const compiled = new vm.Script(script);
const page = 'https://example.org/feed';

function video(src, top = 20, options = {}) {
  return { tagName: 'VIDEO', currentSrc: src, src, readyState: 2, duration: 20,
    videoWidth: 400, paused: false, top,
    getAttribute() { return null; },
    getBoundingClientRect() { return { left: 0, right: 400, top: this.top, bottom: this.top + 220 }; },
    ...options };
}
function harness(initial = []) {
  let videos = initial, interval, observer, now = 10000, serial = 0;
  const listeners = {}, pending = [], admitted = [], focused = [], objects = [], expired = [];
  const requests = [], loads = [];
  let response = { url: 'https://cdn.example.org/stream', status: 200, headers: new Headers({'content-type': 'video/mp4'}) };
  let deferred;
  class FakeURL extends URL {
    static createObjectURL() { return `blob:${page}/${++serial}`; }
    static revokeObjectURL() {}
  }
  class FakeMediaSource { addSourceBuffer(mime) { if (mime === 'invalid') throw Error('codec'); return {mime}; } }
  class FakeXHR {
    open(method, url) { this.url = url; }
    setRequestHeader() {}
    addEventListener(name, cb) { if (name === 'loadend') this.end = cb; }
    getResponseHeader() { return this.mime; }
    send() { loads.push(this); }
  }
  const ctx = {
    URL: FakeURL, Blob, MediaSource: FakeMediaSource, XMLHttpRequest: FakeXHR, Headers,
    location: { href: page }, innerWidth: 400, innerHeight: 800,
    Date: { now: () => now },
    SalviaMedia: {
      onMediaUrlFound: (...args) => admitted.push(args),
      onVisibleMedia: (...args) => focused.push(args),
      onMediaObjectUrl: (...args) => objects.push(args),
      onMediaUrlExpired: (...args) => expired.push(args),
    },
    document: {
      visibilityState: 'visible', documentElement: {},
      addEventListener(name, cb) { listeners[name] = cb; },
      querySelectorAll() { return videos; },
    },
    getComputedStyle(n) { return { visibility: n.hidden ? 'hidden' : 'visible', display: 'block', opacity: '1' }; },
    addEventListener(name, cb) { listeners[name] = cb; },
    MutationObserver: class { constructor(cb) { observer = cb; } observe(options, config) { assert(config.attributes); } },
    setTimeout(cb) { pending.push(cb); }, setInterval(cb) { interval = cb; },
    fetch(...args) { requests.push(args); return deferred || Promise.resolve(response); },
  };
  ctx.window = ctx; ctx.top = ctx;
  vm.createContext(ctx); compiled.runInContext(ctx);
  return { ctx, admitted, focused, objects, expired, requests, loads, pending,
    videos(v) { videos = v; },
    emit(name, target) { assert(listeners[name], name + ' is hooked'); listeners[name]({ target }); },
    mutate() { observer([{ type: 'childList' }]); },
    flush() { while (pending.length) pending.shift()(); },
    tick() { now += 2000; interval(); },
    async fetch(input = 'https://cdn.example.org/stream', init) {
      await ctx.fetch(input, init); await Promise.resolve();
    },
    response(v) { response = v; },
    deferred(p) { deferred = p; },
  };
}
const urls = h => [...new Set(h.admitted.map(row => row[1]))];

test('already loaded inline player is detected immediately, without opening a post', () => {
  const v = video('https://cdn.example.org/inline-no-extension');
  const h = harness([v]);
  assert.deepEqual(urls(h), [v.src]);
  assert.equal(h.focused.at(-1)[1], v.src);
  assert.equal(h.requests.length, 0);
});

test('nested feed inserts and src changes batch once, before the polling fallback', () => {
  const h = harness();
  const v = video('https://cdn.example.org/ad.mp4');
  h.videos([v]); h.mutate(); h.mutate();
  assert.equal(h.pending.length, 1);
  assert.equal(h.admitted.length, 0);
  h.flush();
  assert.equal(h.focused.at(-1)[1], v.src);
  v.currentSrc = v.src = 'https://cdn.example.org/main.mp4'; // same player after Skip
  h.mutate(); h.flush();
  assert.equal(h.focused.at(-1)[1], v.src);
  assert.equal(urls(h).length, 2); // no claim that an on-screen pre-roll was main content
  assert.equal(h.requests.length, 0);
});

test('scrolling changes focus, hidden/offscreen players clear the hint, return reannounces evidence', () => {
  const first = video('https://cdn.example.org/one.mp4');
  const next = video('https://cdn.example.org/two.mp4', 900);
  const h = harness([first, next]);
  first.top = 900; next.top = 20;
  h.emit('scroll'); h.flush();
  assert.equal(h.focused.at(-1)[1], next.src);
  next.hidden = true; h.tick();
  assert.equal(h.focused.at(-1)[1], '');
  next.hidden = false;
  const before = h.admitted.length; h.tick();
  assert(h.admitted.length > before);
  h.ctx.document.visibilityState = 'hidden'; h.emit('visibilitychange');
  assert.equal(h.focused.at(-1)[1], '');
});

test('loaded metadata upgrades a weak source; unloaded alternate sources do not inherit readyState', () => {
  const v = video('https://cdn.example.org/main.mp4', 900, {readyState: 0});
  const h = harness([v]);
  assert.equal(h.admitted.at(-1)[3], 'element-source');
  v.readyState = 2; h.emit('loadedmetadata', v);
  assert.equal(h.admitted.at(-1)[3], 'element-metadata');
  const source = { tagName: 'SOURCE', parentNode: v, getAttribute: n => n === 'src' ? 'https://cdn.example.org/unknown' : '' };
  h.videos([source]); h.mutate(); h.flush();
  assert(!urls(h).includes('https://cdn.example.org/unknown'));
});

test('SPA identity resets report log and restates current player, not previous post', () => {
  const h = harness([video('https://cdn.example.org/old.mp4')]);
  h.ctx.location.href = 'https://example.org/post/two';
  h.videos([video('https://cdn.example.org/new.mp4')]);
  h.ctx.__salviaScan(); h.flush();
  assert.equal(h.focused.at(-1)[0], h.ctx.location.href);
  assert.deepEqual(Array.from(h.ctx.__salviaFound), ['https://cdn.example.org/new.mp4']);
});

test('infinite feed report memory stays bounded and a revisited video is detected again', () => {
  const h = harness();
  for (let i = 0; i < 90; i++) {
    h.videos([video(`https://cdn.example.org/${i}.mp4`)]); h.mutate(); h.flush();
  }
  assert.equal(h.ctx.__salviaFound.length, 60);
  h.videos([video('https://cdn.example.org/0.mp4')]); h.tick();
  assert.equal(h.focused.at(-1)[1], 'https://cdn.example.org/0.mp4');
});

test('fetch inspects actual redirect response headers, never clones body or adds requests', async () => {
  const h = harness();
  const url = 'https://cdn.example.org/final-no-extension';
  h.response({url, status: 200, headers: new Headers({'content-type':'video/mp4'}), clone() { throw Error('no body reads'); }});
  await h.fetch('https://cdn.example.org/redirect');
  assert.deepEqual(urls(h), [url]);
  assert.equal(h.requests.length, 1);
});

test('Request headers, tuples, multiple ranges, 206 and bytestart never admit fragments', async () => {
  for (const headers of [{Range:'bytes=20-'}, [['range','bytes=0-99']], new Headers({Range:'bytes=0-,200-300'})]) {
    const h = harness(); await h.fetch({ url:'https://cdn.example.org/stream', headers });
    assert.equal(h.admitted.length, 0);
  }
  const h = harness();
  await h.fetch('https://cdn.example.org/stream', {headers:{Range:'bytes=0-'}});
  assert.equal(urls(h).length, 1);
  h.response({url:'https://cdn.example.org/partial.mp4',status:206,headers:new Headers({'content-type':'video/mp4'})});
  await h.fetch(); assert.equal(urls(h).length, 1);
  h.response({url:'https://cdn.example.org/partial.mp4?bytestart=0',status:200,headers:new Headers({'content-type':'video/mp4'})});
  await h.fetch(); assert.equal(urls(h).length, 1);
});

test('late response for previous SPA route is not attributed to the new post', async () => {
  const h = harness(); let done;
  h.deferred(new Promise(resolve => { done = resolve; }));
  const request = h.fetch();
  h.ctx.location.href = 'https://example.org/next';
  done({url:'https://cdn.example.org/old.mp4',status:200,headers:new Headers({'content-type':'video/mp4'})});
  await request; assert.equal(h.admitted.length, 0);
});

test('TS, m4s and webmanifest stay rejected even when placed in media elements', () => {
  const h = harness(['clip.ts','part.m4s','site.webmanifest'].map(path => video('https://cdn.example.org/'+path)));
  assert.equal(h.admitted.length, 0);
});

test('MSE provenance stays per-object and survives more than four feed players', () => {
  const h = harness(); const list = [];
  for (let i=0;i<8;i++) {
    const ms = new h.ctx.MediaSource();
    const u = h.ctx.URL.createObjectURL(ms);
    ms.addSourceBuffer('video/mp4; codecs="avc1"'); list.push(u);
  }
  h.videos([video(list[0])]); h.tick();
  assert.equal(h.focused.at(-1)[1], list[0]);
  assert(h.objects.some(row => row[1] === list[0] && row[3] === 'mse'));
  const opaque = 'blob:https://example.org/unknown';
  h.videos([video(opaque)]); h.tick();
  assert(!urls(h).includes(opaque)); // no page-wide last MIME fallback
  const early = new h.ctx.MediaSource(); early.addSourceBuffer('video/webm');
  const earlyUrl = h.ctx.URL.createObjectURL(early);
  assert(h.objects.some(row => row[1] === earlyUrl && row[2] === 'video/webm'));
  const bad = new h.ctx.MediaSource(); const badUrl = h.ctx.URL.createObjectURL(bad);
  assert.throws(() => bad.addSourceBuffer('invalid'));
  assert(!urls(h).includes(badUrl));
});

test('real Blob remains downloadable evidence while MSE is distinct, revocation expires it', () => {
  const h = harness();
  const url = h.ctx.URL.createObjectURL(new Blob(['content'], {type:'video/mp4'}));
  assert.equal(h.objects.length, 0); // creation alone is not playback
  h.videos([video(url)]); h.tick();
  assert(h.objects.some(row => row[1] === url && row[3] === 'file'));
  h.ctx.URL.revokeObjectURL(url);
  assert.equal(h.expired.at(-1)[1], url);
  assert.equal(h.requests.length, 0);
});

test('XHR handles response URL and range without extra send calls', () => {
  const h = harness(); const xhr = new h.ctx.XMLHttpRequest();
  xhr.open('GET','https://cdn.example.org/start'); xhr.send();
  xhr.responseURL='https://cdn.example.org/final'; xhr.mime='video/mp4'; xhr.status=200; xhr.end();
  assert.deepEqual(urls(h),[xhr.responseURL]);
  xhr.open('GET','https://cdn.example.org/part'); xhr.setRequestHeader('Range','bytes=10-'); xhr.send();
  xhr.responseURL='https://cdn.example.org/part'; xhr.end();
  assert.equal(urls(h).length,1); assert.equal(h.loads.length,2);
});

test('DOM snapshot includes page metadata and excludes lifetime sniffer log', () => {
  const text=fs.readFileSync('app/src/main/java/com/salvia/salviabrowxer/feature/browser/TabWebViewStore.kt','utf8');
  const domScript=text.split('private val MEDIA_DETECTION_JS: String = """')[1].split('"""')[0].replace('__MEDIA_EXT__',media);
  const html=vm.runInNewContext(domScript, {
    window:{__salviaFound:['https://cdn.example.org/old.mp4']},
    document:{querySelectorAll(selector) { return selector.startsWith('meta') ?
      [{outerHTML:'<meta property="og:video" content="https://cdn.example.org/main.mp4">'}] : []; }},
  });
  assert(html.includes('main.mp4')); assert(!html.includes('old.mp4'));
});
