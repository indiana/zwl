// Pojedyncze instancje serwisow (wzorzec z probek Garmina:
// callback `method(:...)` musi zyc w instancji klasy, nie w statycznej funkcji).
module Services {
    var fireRisk = new FireRiskService();
    var bans = new BanService();
    var zones = new ZoneFetchService();
}
