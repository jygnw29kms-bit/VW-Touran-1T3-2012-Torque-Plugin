package de.growcentral.touranlive;

/** Fixed vehicle profile derived from the owner's VW PR equipment list and VCDS captures. */
final class VehicleProfile {
    private VehicleProfile() {}

    static final String MODEL = "VW Touran 1T3";
    static final String MODEL_YEAR = "2012";
    static final String PRODUCTION_DATE = "20.09.2011";
    static final String SALES_TYPE = "1T33B1";
    static final String ENGINE = "CAVC";
    static final String ENGINE_TEXT = "1.4 TSI 103 kW / 140 PS Twincharger";
    static final String GEARBOX = "KWB";
    static final String DRIVE = "Frontantrieb";
    static final String COLOR = "2K / A3T";
    static final String TRIM = "STYLE / Sport-Komfort";

    // PR-coded equipment relevant to TouranLive and future CAN/comfort integration.
    static final String[] PRESENT_PR = {
            "1AT ESP", "1N3 Servotronic", "2PP Ledermultifunktionslenkrad",
            "4A3 Sitzheizung vorn", "4L6 Innenspiegel automatisch abblendbar",
            "4LC Radio-/Telefonbedienung am Lenkrad", "6XQ Spiegel elektrisch anklapp-/beheizbar",
            "7X5 Parklenkassistent", "8G1 Fernlichtassistent", "8N3 Regen-/Lichtsensor",
            "8T2 GRA", "8W1 Waschwasserstand", "8WH Nebel-/Abbiegelicht",
            "9AK Climatronic", "9Q5 MFA+", "9Y1 Außentemperaturanzeige",
            "PG1 7-Sitzer", "UG1 Berganfahrassistent"
    };

    static final String[] ABSENT_PR = {
            "1D0 Anhängevorrichtung", "7K0 Reifendruckkontrolle", "7L3 Start/Stopp",
            "7Q0 Werksnavigation", "7Y0 Spurwechselassistent", "9M0 Stand-/Zusatzheizung",
            "9W0 Werks-Telefon", "QK0 Kamera/Distanzsensor Fahrerassistenz", "UF0 externe elektrische Schnittstelle"
    };

    static String compactEquipmentSummary() {
        return "PR present=" + String.join(",", PRESENT_PR) + "\nPR absent=" + String.join(",", ABSENT_PR);
    }
}
