# KontorKaffe 1.1 – byggjekandidat

Åtte inviterte kollegaer, personleg QR, tre klipp, fysisk godkjend nettlesar, ordresporing og bankkortformat. Ingen kundeinnlogging eller telefonløyve.

Ikkje produksjonsklar. Krev nytt, isolert Supabase-prosjekt, konfigurert Edge Function og separat HTTPS-origin (til dømes eit nytt Cloudflare Pages-prosjekt med output `site`). Bruk aldri same nettlesarorigin som andre prosjekt. Set API i `site/config.js`. Ingen private nøklar i klienten. Verifiser database, tilgangar, push og reelle telefonar før bruk. Alle data og nøklar må vera spesifikke for KontorKaffe. Ingen kopling til andre prosjekt.