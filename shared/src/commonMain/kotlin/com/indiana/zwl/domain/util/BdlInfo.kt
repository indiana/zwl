package com.indiana.zwl.domain.util

/**
 * Centralny katalog opisów (tooltipów) i tytułów artykułów Wikipedii dla kodów BDL.
 *
 * Klucze map to SUROWE kody BDL (tak jak w tabelach referencyjnych, np. "O SPO", "2 PIĘT", "ŚW.KB").
 * Mapy są normalizowane przy inicjalizacji przez RdlpMapper.normalize(), a funkcje wyszukujące
 * normalizują kod wejściowy — dzięki temu klucze w tym pliku można czytać bez przeliczania.
 *
 * Nazwy wyświetlane (polskie tłumaczenia) NIE są trzymane w tym pliku — pochodzą z RdlpMapper.
 * Nieznane kody zwracają null (UI renderuje wtedy zwykły tekst, bez tooltipa i bez linku).
 */
object BdlInfo {

    const val WIKIPEDIA_BASE_URL = "https://pl.wikipedia.org/wiki/"

    // ---------------------------------------------------------------- species_cd
    // Kod BDL -> tytuł artykułu na pl.wikipedia.org
    private val speciesWikipedia: Map<String, String> = mapOf(
        "SO" to "Sosna zwyczajna",
        "ŚW" to "Świerk pospolity",
        "DB" to "Dąb szypułkowy",
        "DB.S" to "Dąb szypułkowy",
        "DB.B" to "Dąb bezszypułkowy",
        "DB.C" to "Dąb czerwony",
        "BK" to "Buk zwyczajny",
        "BRZ" to "Brzoza brodawkowata",
        "BRZ.O" to "Brzoza omszona",
        "JD" to "Jodła pospolita",
        "JD.O" to "Jodła olbrzymia",
        "JD.J" to "Jodła jednobarwna",
        "OL" to "Olcha czarna",
        "OL.S" to "Olcha szara",
        "OS" to "Topola osika",
        "WB" to "Wierzba",
        "WZ" to "Wiąz",
        "WZ.S" to "Wiąz szypułkowy",
        "JS" to "Jesion wyniosły",
        "JS.A" to "Jesion amerykański",
        "JS.P" to "Jesion pensylwański",
        "KL" to "Klon zwyczajny",
        "KL.P" to "Klon polny",
        "JKL" to "Klon jesionolistny",
        "JW" to "Klon jawor",
        "LP" to "Lipa drobnolistna",
        "MD" to "Modrzew europejski",
        "DG" to "Daglezja zielona",
        "GB" to "Grab pospolity",
        "AK" to "Robinia akacjowa",
        "CZR" to "Czereśnia ptasia",
        "CZR.P" to "Czereśnia ptasia",
        "GR" to "Grusza pospolita",
        "JRZ" to "Jarząb pospolity",
        "JRZ.B" to "Jarząb brekinia",
        "ORZ.C" to "Orzech czarny",
        "SO.B" to "Sosna banksa",
        "SO.C" to "Sosna czarna",
        "SO.K" to "Kosodrzewina",
        "SO.S" to "Sosna smołowa",
        "SO.WE" to "Sosna wejmutka",
        "TP" to "Topola",
        "TP.M" to "Topola",
        "ŚW.KB" to "Świerk kaukaski",
        "ŚW.SR" to "Świerk kłujący",
        "ŻYW.O" to "Żywotnik olbrzymi",
        "IWA" to "Wierzba iwa"
    ).mapKeys { RdlpMapper.normalize(it.key) }

    // ---------------------------------------------------------------- forest_fun
    private val forestFunTooltips: Map<String, String> = mapOf(
        "GOSP" to "Las pełniący przede wszystkim funkcję produkcyjną — głównym celem gospodarki leśnej jest tu pozyskiwanie drewna.",
        "O SPO" to "Lasy pełniące pozaprodukcyjne funkcje ochronne, takie jak ochrona gleb, wód, klimatu czy krajobrazu. Prowadzona w nich gospodarka leśna jest podporządkowana celom ochronnym.",
        "REZ" to "Las objęty ochroną rezerwatową, w którym nadrzędnym celem jest zachowanie przyrody w stanie naturalnym lub zbliżonym do naturalnego.",
        "REZ CZ" to "Rezerwat częściowy — dopuszcza się w nim wybrane zabiegi ochronne i gospodarcze, służące utrzymaniu lub odtworzeniu właściwego stanu ekosystemu.",
        "REZ Ś" to "Rezerwat ścisły — obowiązuje w nim całkowita, bierna ochrona przyrody, bez zabiegów gospodarczych i ochronnych, pozostawiająca procesy naturalne."
    ).mapKeys { RdlpMapper.normalize(it.key) }

    // ---------------------------------------------------------------- stand_stru ("budowa pionowa")
    private val standStruTooltips: Map<String, String> = mapOf(
        "DRZEW" to "Drzewostan jednopiętrowy, w którym korony drzew tworzą jeden, zwarty poziom (piętro).",
        "2 PIĘT" to "Drzewostan dwupiętrowy, w którym pod okapem górnego piętra rozwija się drugie, niższe piętro drzew — często przyszłe odnowienie.",
        "W PIĘT" to "Drzewostan wielopiętrowy, w którym korony drzew układają się w kilka wyraźnych poziomów (pięter).",
        "KO" to "Drzewostan w klasie odnowienia — pod jego okapem rozpoczęło się już odnowienie, czyli pojawiło się młode pokolenie drzew.",
        "KDO" to "Drzewostan w klasie do odnowienia — jest dojrzały i zakwalifikowany do odnowienia, ale proces wymiany pokoleniowej jeszcze się nie rozpoczął.",
        "SP" to "Drzewostan o budowie przerębowej — charakteryzuje się ciągłą, zróżnicowaną strukturą wiekową i wysokościową, z drzewami wszystkich klas wieku obok siebie."
    ).mapKeys { RdlpMapper.normalize(it.key) }

    // ---------------------------------------------------------------- site_type ("typ siedliskowy lasu")
    private val siteTypeTooltips: Map<String, String> = mapOf(
        "BB" to "Trwałe torfowiska i zabagnienia z wysokim poziomem wody gruntowej. Niskoprodukcyjne bory, w których obok sosny, czasem brzozy, występuje warstwa krzewinek, takich jak bagno zwyczajne i borówki.",
        "BGB" to "Bagienny wariant boru górskiego, występujący na zabagnionych, torfowych siedliskach w górach. Drzewostan tworzy głównie świerk z domieszką sosny i brzozy.",
        "BGŚW" to "Bór występujący w reglu dolnym, na umiarkowanie wilgotnych glebach górskich. Drzewostan tworzy świerk z domieszką jodły i buka.",
        "BGW" to "Wilgotny wariant boru górskiego, z okresowo podsiąkającą wodą gruntową. W drzewostanie dominuje świerk, któremu towarzyszą jodła i brzoza.",
        "BMB" to "Siedlisko bagienne i mokradłowe, z torfowiskami i wysokim poziomem wody. Drzewostan tworzą olsza, brzoza i sosna, często z krzewinkami w runie.",
        "BMGB" to "Bagienny wariant boru mieszanego górskiego, na trwale podmokłych siedliskach w górach. W drzewostanie świerk z domieszką jodły i brzozy.",
        "BMGŚW" to "Świeży bór mieszany regla dolnego, o umiarkowanej żyzności. Drzewostan tworzą świerk, jodła i buk.",
        "BMGW" to "Wilgotny wariant boru mieszanego górskiego, z okresowo podsiąkającą wodą. W drzewostanie świerk z jodłą, brzozą i olszą.",
        "BMŚW" to "Umiarkowanie żyzne, świeże siedlisko na glebach bielicowych i rdzawych. Drzewostan tworzy sosna z dębem, świerkiem i brzozą.",
        "BMW" to "Wilgotniejszy wariant boru mieszanego, z okresowo podsiąkającą wodą gruntową. Większy udział świerka, brzozy i olszy w drzewostanie.",
        "BMWYŻ" to "Wyżynny wariant boru mieszanego świeżego, na zróżnicowanych, często falistych terenach. Żyźniejsze siedlisko strefy wyżynnej, w którym obok sosny często rośnie dąb i buk.",
        "BMWYŻW" to "Wilgotny wariant boru mieszanego wyżynnego, z okresowo podsiąkającą wodą na zboczach. W drzewostanie sosna z domieszką dębu, buka i świerka.",
        "BS" to "Najuboższe, suche siedliska piaszczyste, z głęboko zalegającą wodą gruntową. Słabo produkcyjne drzewostany sosnowe, często z porostami w runie.",
        "BŚW" to "Ubogie gleby bielicowe piaszczyste, typowe dla borów. Bory sosnowe (w górach świerkowe), z runem borówkowym i krzewinkami.",
        "BW" to "Siedliska wilgotne, z okresowo podsiąkającą wodą gruntową. Drzewostan tworzy sosna z brzozą i świerkiem.",
        "BWG" to "Siedlisko piętra regla górnego, o surowym klimacie i krótkim okresie wegetacyjnym. Świerczyny sięgające górnej granicy lasu.",
        "LGŚW" to "Żyzne siedlisko górskie, występujące głównie w reglu dolnym. Drzewostan tworzą buk, jodła i świerk.",
        "LGW" to "Wilgotny wariant lasu górskiego, na zboczach z okresowo spływającą wodą. W drzewostanie jodła, świerk, jawor i jesion.",
        "LMB" to "Bagienny wariant lasu mieszanego, z trwałym zabagnieniem i wysokim poziomem wody. Drzewostan tworzą olsza i brzoza.",
        "LMG" to "Las mieszany regla dolnego, o dobrej żyzności. Drzewostan tworzą buk, jodła i świerk.",
        "LMGŚW" to "Las mieszany regla dolnego, o dobrej żyzności. Drzewostan tworzą buk, jodła i świerk.",
        "LMGW" to "Wilgotny wariant lasu mieszanego górskiego, z okresowo podsiąkającą wodą. W drzewostanie jodła, świerk i jawor.",
        "LMŚW" to "Żyźniejsze, świeże siedlisko, przejściowe między lasem a borem. Drzewostan tworzą dąb, buk, grab, sosna i świerk.",
        "LMW" to "Wilgotny wariant lasu mieszanego, z okresowo podsiąkającą wodą gruntową. W drzewostanie olsza, jesion, dąb i świerk.",
        "LMWYŻ" to "Wyżynny wariant lasu mieszanego świeżego, na żyźniejszych glebach strefy wyżynnej. W drzewostanie często buk i dąb obok sosny i świerka.",
        "LMWYŻW" to "Wilgotny wariant lasu mieszanego wyżynnego, na zboczach z okresowo spływającą wodą. W drzewostanie dąb, buk, jesion i świerk.",
        "LŚW" to "Żyzne gleby brunatne i rdzawe, typowe dla lasów nizinnych. Drzewostan tworzą buk, dąb, grab, lipa, klon i jesion.",
        "LW" to "Żyzne i wilgotne siedlisko, z okresowo podsiąkającą wodą gruntową. Drzewostan tworzą olsza, jesion, wiąz i dąb.",
        "LWYŻ" to "Wyżynny wariant lasu świeżego, na żyznych glebach strefy wyżynnej. W drzewostanie często buk i dąb, z domieszką grabu i klonu.",
        "LWYŻŚ" to "Wyżynny wariant lasu świeżego, na żyznych glebach strefy wyżynnej. W drzewostanie często buk i dąb, z domieszką grabu i klonu.",
        "LWYŻW" to "Wilgotny wariant lasu wyżynnego, na zboczach z okresowo spływającą wodą. W drzewostanie buk, dąb, jesion i klon.",
        "LŁ" to "Żyzne mady w dolinach rzek niżowych, okresowo zalewane podczas wezbrań. Drzewostan tworzą jesion, wiąz, dąb, klon i olsza.",
        "LŁG" to "Występuje jako wąski pas wzdłuż koryt potoków i rzek górskich, głównie w reglu dolnym poniżej 500–600 m n.p.m. Porasta żyzne mady, a jego drzewostan tworzą gatunki lubiące wilgoć, takie jak olsza szara i jesion.",
        "LŁWYŻ" to "Łęg występujący w dolinach rzek strefy wyżynnej, na żyznych madach okresowo zalewanych. Drzewostan tworzą jesion, wiąz, dąb i klon.",
        "OL" to "Trwale podmokłe torfowiska niskie, z wysokim poziomem wody gruntowej. Drzewostan tworzy olsza czarna, często o charakterystycznej kępkowo-dolinkowej strukturze terenu.",
        "OLJ" to "Przepływowe torfowiska niskie, zasilane żyzną, przepływającą wodą gruntową. Drzewostan tworzą olsza i jesion.",
        "OLJG" to "Ols jesionowy występujący wzdłuż górskich potoków, na żyznych, przepływowych siedliskach. Drzewostan tworzą olsza szara i jesion.",
        "OLJWYŻ" to "Wyżynny wariant olsu jesionowego, w dolinach potoków strefy wyżynnej. Drzewostan tworzą olsza i jesion, z domieszką wiązu i klonu.",
        // pełne warianty uproszczonych kodów, sporadycznie emitowane przez API
        "BMWYŻŚW" to "Wyżynny wariant boru mieszanego świeżego, na zróżnicowanych, często falistych terenach. Żyźniejsze siedlisko strefy wyżynnej, w którym obok sosny często rośnie dąb i buk.",
        "LMWYŻŚW" to "Wyżynny wariant lasu mieszanego świeżego, na żyźniejszych glebach strefy wyżynnej. W drzewostanie często buk i dąb obok sosny i świerka.",
        "LWYŻŚW" to "Wyżynny wariant lasu świeżego, na żyznych glebach strefy wyżynnej. W drzewostanie często buk i dąb, z domieszką grabu i klonu."
    ).mapKeys { RdlpMapper.normalize(it.key) }

    // ---------------------------------------------------------------- prot_categ ("kategoria ochrony", Art. 15 ustawy o lasach)
    private val protCategTooltips: Map<String, String> = mapOf(
        "OCH GLEB" to "Lasy glebochronne — ich podstawową funkcją jest ochrona gleby przed erozją, zwłaszcza na stromych zboczach i terenach zagrożonych osuwiskami.",
        "OCH WOD" to "Lasy wodochronne — pełnią funkcję regulacji stosunków wodnych: chronią źródła, zbiorniki i cieki wodne oraz ograniczają spływ powierzchniowy.",
        "OCH USZK" to "Lasy trwale uszkodzone na skutek oddziaływania przemysłu (np. emisji zanieczyszczeń), w których prowadzi się zabiegi poprawiające ich stan.",
        "OCH NAS" to "Gospodarcze drzewostany nasienne — przeznaczone do pozyskiwania nasion i materiału hodowlanego o wysokiej jakości genetycznej.",
        "OCH OSTOJ" to "Lasy stanowiące ostoje zwierząt — miejsca bytowania, rozrodu i żerowania cennych gatunków, w których ogranicza się ich niepokojenie.",
        "OCH CENNE" to "Cenne fragmenty przyrody — obszary wyróżniające się występowaniem rzadkich gatunków roślin, zwierząt lub siedlisk, wymagające szczególnej ochrony.",
        "OCH BADAW" to "Powierzchnie badawczo-doświadczalne — służą prowadzeniu badań naukowych i doświadczeń leśnych.",
        "OCH OBR" to "Lasy obronne — pełnią funkcje związane z obronnością kraju, m.in. maskują lub osłaniają obiekty i tereny o znaczeniu wojskowym.",
        "OCH MIAST" to "Lasy położone w granicach administracyjnych miast, pełniące ważne funkcje społeczne — rekreacyjne, wypoczynkowe, zdrowotne i klimatyczne.",
        "OCH UZDR" to "Lasy uzdrowiskowe — położone w strefach ochronnych uzdrowisk, pełnią funkcje zdrowotne i klimatyczne dla kuracjuszy i mieszkańców."
    ).mapKeys { RdlpMapper.normalize(it.key) }

    // ---------------------------------------------------------------- rotat_age (opcjonalny tooltip ogólny)
    val rotationAgeTooltip: String =
        "Wiek rębności — wiek, w którym drzewostan osiąga dojrzałość rębną i może być przeznaczony do odnowienia, czyli wycięcia i wymiany pokoleniowej."

    // ---------------------------------------------------------------- soil_subtype_cd / veg_cover_cd (opisy taksacyjne SILP;
    // wartości są dekodowane przez BDL dla każdego kodu — tooltip wyjaśnia dany typ gleby / rodzaj pokrywy)

    private val soilTypeTooltips: Map<String, String> = mapOf(
        "RDB" to "Gleby rdzawe bielicowe — ubogie, kwaśne gleby wytworzone z piasków. Rdzawa barwa pochodzi z procesu " +
            "rdzawienia (wytrącanie i impregnacja tlenków żelaza), a dodatkowo w profilu zachodzi bielicowanie — " +
            "wymywanie związków żelaza i próchnicy w głąb. Typowe dla borów sosnowych.",
        "RDBR" to "Gleby rdzawe brunatne — specyficzny podtyp gleb rdzawych, które powstają na cięższych piaskach. " +
            "W ich profilu zachodzą jednocześnie dwa procesy: rdzawienia (tworzenie rdzawej barwy) oraz brunatnienia " +
            "(uwolnienie tlenków żelaza i zmiana odcienia na brązowy). Zwykle żyźniejsze od gleb rdzawych bielicowych.",
        "RDW" to "Gleby rdzawe właściwe — klasyczne gleby rdzawe bez wyraźnych cech bielicowania czy brunatnienia: " +
            "ubogie, kwaśne, luźne, piaskowe. Typowe dla borów sosnowych.",
        "BW" to "Gleby bielicowe właściwe — wytworzone z piasków pod wpływem intensywnego bielicowania: wymywanie " +
            "żelaza i próchnicy tworzy jasną warstwę wierzchnią i ciemniejszą warstwę wzbogacenia. Ubogie, kwaśne — " +
            "siedliska borowe.",
        "BGW" to "Gleby glejo-bielicowe właściwe — gleby bielicowe z procesem oglejenia (rdzawe i szare plamy w profilu) " +
            "wskazującym okresową podmokłość; zwykle w zagłębieniach terenu.",
        "BRK" to "Gleby brunatne kwaśne — wytworzone z piasków gliniastych lub glin bez węglanów; kwaśne, " +
            "umiarkowanie żyzne; brunatnienie nadaje profilowi brązowe odcienie. Typowe dla lasów mieszanych.",
        "BRWY" to "Gleby brunatne wyługowane — gleby brunatne z wypłukanymi węglanami (woda wypłukuje wapń z " +
            "powierzchniowych warstw); umiarkowanie żyzne, typowe dla lasów mieszanych świeżych.",
        "OGW" to "Gleby opadowo-glejowe właściwe — powstają tam, gdzie woda opadowa zalega na nieprzepuszczalnym " +
            "podłożu: naprzemienne uwilgotnienie i przesychanie daje plamy oglejenia. Lasy mieszane, często wilgotne.",
        "ARB" to "Arenosole bielicowane — młode, słabo ukształtowane gleby piaszczyste (wydmy, piaski nawiane) " +
            "z początkowym bielicowaniem; ubogie i kwaśne — siedliska borowe.",
        "RW" to "Rędziny właściwe — płytkie gleby wytworzone ze skał węglanowych (wapienie, margle, dolomity); " +
            "żyzne, zasobne w wapń, o obojętnym odczynie. Często pod lasami bukowymi i dębowymi."
    )

    private val soilFamilyTooltips: List<Pair<String, String>> = listOf(
        "RD" to "Gleby rdzawe — ubogie, kwaśne gleby piaskowe i słabogliniaste; rdzawa barwa poziomu wzbogacenia " +
            "pochodzi z oddziaływania rdzawienia (wytrącanie tlenków żelaza). Typowe dla borów.",
        "RN" to "Rędziny (wariant) — gleby wytworzone ze skał węglanowych: płytkie, żyzne, zasobne w wapń.",
        "R" to "Rędziny — płytkie gleby wytworzone ze skał węglanowych (wapienie, margle); żyzne, zasobne " +
            "w wapń, o obojętnym odczynie.",
        "BR" to "Gleby brunatne — wytworzone z glin lub piasków gliniastych pod wpływem brunatnienia (uwolnienie " +
            "tlenków żelaza, brązowe odcienie); umiarkowanie żyzne — lasy mieszane.",
        "BL" to "Gleby bielicowe — kwaśne, ubogie gleby piaskowe, w których bielicowanie (wymywanie żelaza i " +
            "próchnicy w głąb profilu) tworzy jasną warstwę wierzchnią; siedliska borowe.",
        "BG" to "Gleby glejo-bielicowe — gleby bielicowe z cechami oglejenia wskazującymi okresową podmokłość; " +
            "siedliska borowe i lasów mieszanych, zwykle wilgotniejsze.",
        "BW" to "Gleby bielicowe właściwe — kwaśne, ubogie gleby piaskowe ukształtowane przez bielicowanie; " +
            "siedliska borowe.",
        "B" to "Gleby bielicowe — kwaśne, ubogie gleby piaskowe, w których bielicowanie (wymywanie żelaza i " +
            "próchnicy w głąb profilu) tworzy jasną warstwę wierzchnią; siedliska borowe.",
        "OG" to "Gleby opadowo-glejowe — podmokłe od zalegającej wody opadowej na nieprzepuszczalnym podłożu; " +
            "naprzemienne wilgotnienie i przesychanie tworzy plamy oglejenia; lasy mieszane wilgotne.",
        "G" to "Gleby gruntowo-glejowe — ukształtowane pod wpływem wody gruntowej; okresowo lub stale podmokłe; " +
            "olsy, łęgi i lasy wilgotne.",
        "CZ" to "Czarnoziemy — najżyźniejsze gleby, bogate w próchnicę, wytworzone z utworów węglanowych; " +
            "bardzo żyzne siedliska.",
        "C" to "Czarne ziemie — żyzne gleby bogate w próchnicę, wytworzone z utworów węglanowych; siedliska " +
            "lasów mieszanych i liściastych.",
        "D" to "Gleby deluwialne — wytworzone z materiału przemieszczonego po stoku przez spłukiwanie i grawitację; " +
            "właściwości zależą od materiału macierzystego.",
        "MD" to "Mady — młode gleby z naniesionego przez wodę materiału aluwialnego; żyzność zróżnicowana, " +
            "zależna od granulacji naniesionego materiału.",
        "ML" to "Mady łąkowe — młode gleby aluwialne terenów zalewowych; żyzność zależna od materiału.",
        "MR" to "Mady rzeczne — młode gleby z osadów rzecznych; właściwości zależą od granulacji materiału.",
        "MN" to "Mady — młode gleby z naniesionego przez wodę materiału aluwialnego; żyzność zależna od materiału.",
        "M" to "Mady — młode gleby z naniesionego przez wodę materiału aluwialnego; żyzność zależna od materiału.",
        "MT" to "Gleby murszowate — gleby organiczne poddane murszeniu: wysychanie i mineralizacja tworzy " +
            "strukturę murszu; podmokłe, zwykle silnie kwaśne.",
        "T" to "Gleby torfowe — wytworzone z torfu na zabagnionych terenach; podmokłe; torfowiska wysokie są " +
            "silnie kwaśne i ubogie, niskie — żyzniejsze.",
        "AK" to "Gleby węglanowe (akumulacyjne) — wytworzone z materiału bogatego w węglan wapnia; płytkie, " +
            "żyzne, o obojętnym lub zasadowym odczynie.",
        "AR" to "Arenosole — młode, słabo ukształtowane gleby piaszczyste (piaski nawiane i wydmy); ubogie, " +
            "kwaśne — siedliska borowe.",
        "AU" to "Gleby urbic (antropogeniczne) — zmienione przez działalność człowieka (nasypy, hałdy, tereny " +
            "miejskie); właściwości mocno zróżnicowane.",
        "IR" to "Gleby inicjalne rdzawe — początkowe, słabo ukształtowane stadium rozwoju gleb rdzawych na piaskach.",
        "IS" to "Gleby inicjalne skaliste — początkowe stadium rozwoju gleby na litej skale lub kamieńcu."
    )

    private val groundCoverTooltips: Map<String, String> = mapOf(
        "NAGA" to "Pokrywa naga — powierzchnia gleby pozbawiona roślinności przyziemnej; typowa dla świeżo " +
            "przygotowanej gleby, wydeptywanych powierzchni lub zwartych drzewostanów.",
        "ZIEL" to "Pokrywa zielna — glebę pokrywa bujne runo traw i ziół; wskazuje siedlisko żyźniejsze i " +
            "umiarkowanie wilgotne.",
        "ZAD" to "Pokrywa zadarniona — glebę pokrywa zwarta darń traw; zadarnienie utrudnia odnowienie " +
            "naturalne drzew.",
        "SZAD" to "Pokrywa silnie zadarniona — zwarta, gęsta darń traw pokrywająca większość powierzchni " +
            "gleby; silna konkurencja dla odnowień, zwykle siedlisko żyzniejsze.",
        "MSZ" to "Pokrywa mszysta-kobiercowa — glebę pokrywają kobierce mchów; wskazuje siedlisko ubogie, " +
            "kwaśne i często wilgotne (bory).",
        "MSZC" to "Pokrywa mszysto-czernicowa — mchy wraz z borówką czernicą; typowe dla borów i lasów " +
            "mieszanych na siedliskach kwaśnych i ubogich."
    )

    private val SOIL_TYPE_GENERIC_TOOLTIP =
        "Typ gleby wg gleboznawczej klasyfikacji gleb leśnych — określa genezę i właściwości podłoża: " +
            "żyzność, granulację i uwilgotnienie."

    private val GROUND_COVER_GENERIC_TOOLTIP =
        "Pokrywa — roślinność przykrywająca powierzchnię gleby: zadarnienie, mchy, runo; wpływa na " +
            "odnowienie lasu i charakter siedliska."

    fun soilTypeTooltip(code: String): String {
        val normalized = RdlpMapper.normalize(code)
        soilTypeTooltips[normalized]?.let { return it }
        soilFamilyTooltips.firstOrNull { normalized.startsWith(it.first) }?.let { return it.second }
        return SOIL_TYPE_GENERIC_TOOLTIP
    }

    fun groundCoverTooltip(code: String): String {
        val normalized = RdlpMapper.normalize(code)
        groundCoverTooltips[normalized]?.let { return it }
        return GROUND_COVER_GENERIC_TOOLTIP
    }

    // ---------------------------------------------------------------- lookups (null = brak danych, UI pokazuje zwykły tekst)

    fun wikipediaTitleForSpecies(code: String): String? =
        speciesWikipedia[RdlpMapper.normalize(code)]

    fun tooltipForForestFun(code: String): String? =
        forestFunTooltips[RdlpMapper.normalize(code)]

    fun tooltipForStandStru(code: String): String? =
        standStruTooltips[RdlpMapper.normalize(code)]

    fun tooltipForSiteType(code: String): String? =
        siteTypeTooltips[RdlpMapper.normalize(code)]

    fun tooltipForProtCateg(code: String): String? =
        protCategTooltips[RdlpMapper.normalize(code)]
}
