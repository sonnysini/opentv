/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.pairing

import android.content.Context
import androidx.annotation.StringRes
import app.opentv.R
import app.opentv.core.AppText

/**
 * The page the phone sees.
 *
 * Deliberately one self-contained document with no external requests of any kind — no fonts,
 * no frameworks, no analytics. The phone is talking to a television on the local network,
 * which may have no route to the internet at all, and a page that needs a CDN to render would
 * simply fail.
 *
 * Written in the phone's language ([lang] is a context built from its Accept-Language header,
 * see [AppText.forBrowser]), not the TV's.
 */
object PairingPage {

    fun form(token: String, error: String?, lang: Context?): String = page(lang,
        """
        <h1>${s(lang, R.string.pp_title)}</h1>
        <p class="sub">${s(lang, R.string.pp_sub)}</p>

        ${if (error != null) "<div class=\"error\">${escape(error)}</div>" else ""}

        <form method="post" action="/submit" autocomplete="off">
          <input type="hidden" name="t" value="${escape(token)}">

          <div class="seg">
            <label class="segbtn">
              <input type="radio" name="kind" value="xtream" checked onchange="toggle()">
              <span>${s(lang, R.string.pp_kind_xtream)}</span>
            </label>
            <label class="segbtn">
              <input type="radio" name="kind" value="m3u" onchange="toggle()">
              <span>${s(lang, R.string.pp_kind_m3u)}</span>
            </label>
          </div>

          <label for="name">${s(lang, R.string.pp_name)} <span class="opt">${s(lang, R.string.pp_optional)}</span></label>
          <input id="name" name="name" type="text" placeholder="${s(lang, R.string.pp_name_placeholder)}">

          <label for="url" id="url-label">${s(lang, R.string.pp_server)}</label>
          <input id="url" name="url" type="url" inputmode="url" required
                 placeholder="http://example.com:8080"
                 autocapitalize="off" autocorrect="off" spellcheck="false">
          <p class="hint" id="url-hint">
            ${s(lang, R.string.pp_server_hint)}
          </p>

          <div id="creds">
            <label for="username">${s(lang, R.string.pp_username)}</label>
            <input id="username" name="username" type="text"
                   autocapitalize="off" autocorrect="off" spellcheck="false">

            <label for="password">${s(lang, R.string.pp_password)}</label>
            <input id="password" name="password" type="password">
          </div>

          <label for="epg">${s(lang, R.string.pp_epg)} <span class="opt">${s(lang, R.string.pp_optional)}</span></label>
          <input id="epg" name="epg" type="url" inputmode="url"
                 autocapitalize="off" autocorrect="off" spellcheck="false">
          <p class="hint">${s(lang, R.string.pp_epg_hint)}</p>

          <hr>

          <label for="code">${s(lang, R.string.pp_code)}</label>
          <input id="code" name="code" type="text" inputmode="numeric" pattern="[0-9]*"
                 maxlength="6" required class="code" placeholder="000000"
                 autocomplete="one-time-code">
          <p class="hint">${s(lang, R.string.pp_code_hint)}</p>

          <button type="submit">${s(lang, R.string.pp_send)}</button>
        </form>

        <p class="privacy">${s(lang, R.string.pp_privacy)}</p>

        <script>
          function toggle() {
            var isM3u = document.querySelector('input[name=kind]:checked').value === 'm3u';
            document.getElementById('creds').style.display = isM3u ? 'none' : 'block';
            document.getElementById('url-label').textContent =
              isM3u ? ${js(lang, R.string.pp_playlist_url)} : ${js(lang, R.string.pp_server)};
            document.getElementById('url-hint').textContent = isM3u
              ? ${js(lang, R.string.pp_playlist_hint)}
              : ${js(lang, R.string.pp_server_hint_short)};
            document.getElementById('username').required = !isM3u;
            document.getElementById('password').required = !isM3u;
          }
          toggle();
        </script>
        """.trimIndent(),
    )

    fun message(title: String, body: String, lang: Context?): String = page(lang,
        """
        <h1>${escape(title)}</h1>
        <p class="sub">${escape(body)}</p>
        """.trimIndent(),
    )

    private fun page(lang: Context?, content: String): String = """
        <!DOCTYPE html>
        <html lang="${s(lang, R.string.pp_html_lang)}">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <meta name="robots" content="noindex, nofollow">
        <title>${s(lang, R.string.pp_title)}</title>
        <style>
          :root { --bg:#07080c; --surface:#12141d; --line:#2a2f3d; --text:#e8eaf0;
                  --muted:#a8aec0; --accent:#7c93ff; --err:#ff6b6b; }
          * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
          body { margin:0; background:var(--bg); color:var(--text); padding:28px 20px 64px;
                 font:16px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
          .wrap { max-width:460px; margin:0 auto; }
          h1 { font-size:1.7rem; margin:0 0 6px; letter-spacing:-.02em; }
          .sub { color:var(--muted); margin:0 0 24px; }
          label { display:block; font-size:.9rem; font-weight:600; margin:18px 0 6px; }
          .opt { font-weight:400; color:var(--muted); font-size:.82rem; }
          input[type=text], input[type=url], input[type=password] {
            width:100%; padding:14px; font-size:17px; /* 16px+ stops iOS zooming on focus */
            background:var(--surface); color:var(--text);
            border:1px solid var(--line); border-radius:10px; }
          input:focus { outline:none; border-color:var(--accent); }
          .code { font-size:26px; letter-spacing:.34em; text-align:center; font-weight:600; }
          .hint { color:var(--muted); font-size:.85rem; margin:6px 0 0; }
          .seg { display:flex; gap:8px; margin-bottom:6px; }
          .segbtn { flex:1; margin:0; }
          .segbtn input { position:absolute; opacity:0; pointer-events:none; }
          .segbtn span { display:block; text-align:center; padding:12px;
            background:var(--surface); border:1px solid var(--line); border-radius:10px;
            font-weight:500; font-size:.95rem; }
          .segbtn input:checked + span { background:#3a447a; border-color:var(--accent); }
          button { width:100%; margin-top:26px; padding:17px; font-size:17px; font-weight:600;
            background:var(--accent); color:#0a0c14; border:0; border-radius:11px; }
          button:active { opacity:.85; }
          hr { border:0; border-top:1px solid var(--line); margin:28px 0 4px; }
          .error { background:#2a1416; border:1px solid var(--err); color:#ffc9c9;
            padding:12px 14px; border-radius:10px; margin-bottom:18px; font-size:.92rem; }
          .privacy { color:var(--muted); font-size:.82rem; margin-top:30px; }
        </style>
        </head>
        <body><div class="wrap">
        $content
        </div></body>
        </html>
    """.trimIndent()

    /** A string in the phone's language, HTML-escaped. */
    private fun s(lang: Context?, @StringRes id: Int): String =
        escape(lang?.getString(id) ?: AppText.get(id))

    /** A string in the phone's language as a JavaScript string literal. */
    private fun js(lang: Context?, @StringRes id: Int): String {
        val raw = lang?.getString(id) ?: AppText.get(id)
        return "'" + raw.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\u003c") + "'"
    }

    /** Minimal HTML escaping. Everything interpolated into the page goes through here. */
    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
