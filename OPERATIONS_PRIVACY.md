# KontorKaffe 2.5 – privat drift
- Frivillig klubb med åtte kort; ingen kommunal integrasjon, kundekonto eller installasjon på kundetelefon.
- Bruk privat utstyr og godkjend nettilgang. Avklar arbeidsgjevar sine reglar før pilot. Ingen kommunal logo, passord eller arbeidsdata.
- Kallenamn er valfritt. Slett medlemskap fjernar aktive databaserader for bestilling og nullstiller medlemsplassen; gamle QR-kort vert ugyldige. Leverandørloggar og eventuelle backupkopiar er separate.
- Behald originalt privat QR-ark offline. Ikkje last det opp i repository. Ved misting: erstatt berre aktuelt kort. Ved ny telefon: nullstill nettlesarbinding, godkjenn fysisk.
- Backup: bruk isolert Supabase-prosjekt og offisiell databaseeksport til kryptert privat lagring. Eksporten kan innehalda QR-hashar, nettlesarnøklar, ordrehistorikk og varslingsnøklar; del aldri offentleg. Test gjenoppretting i eit separat KontorKaffe-testmiljø, ikkje i produksjon. Slett gamle kopiar etter fastsett retensjon. Sikkerheitskopi og gjenoppretting er ikkje verifisert av dette bygget.
- Barista: slå på telefonvarsling i nettlesar og send testvarsel. APK har berre ordrelyd i framgrunnen. APK er debug-signert og ikkje godkjend for kommunalt utstyr.
- Admin MFA krev eigen kontrollert innrullering og test før tvang; ikkje lås ute eksisterande administrator.
