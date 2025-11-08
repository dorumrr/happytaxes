package io.github.dorumrr.happytaxes.data.currency

/**
 * Currency data with native language descriptions.
 * 
 * Format: "CODE - Description in native language"
 * 
 * Note: Some currencies with complex scripts (Arabic, Chinese, Japanese, etc.) 
 * are excluded to avoid character encoding issues. Users can select "Other" for these.
 */
data class CurrencyInfo(
    val code: String,
    val description: String
) {
    val displayText: String
        get() = "$code - $description"
}

/**
 * All supported currencies with descriptions in their native languages.
 * Currencies are alphabetically sorted by code.
 */
object CurrencyData {
    val allCurrencies = listOf(
        // A
        CurrencyInfo("AUD", "Australian Dollar"),
        CurrencyInfo("ARS", "Peso argentino"),
        CurrencyInfo("ALL", "Leku shqiptar"),
        CurrencyInfo("AMD", "Հայկական դրամ"),
        CurrencyInfo("ANG", "Antilliaanse gulden"),
        CurrencyInfo("AOA", "Kwanza angolano"),
        CurrencyInfo("AWG", "Arubaanse florin"),
        CurrencyInfo("AZN", "Azərbaycan manatı"),
        
        // B
        CurrencyInfo("BAM", "Konvertibilna marka"),
        CurrencyInfo("BBD", "Barbadian Dollar"),
        CurrencyInfo("BDT", "Bangladeshi Taka"),
        CurrencyInfo("BGN", "Български лев"),
        CurrencyInfo("BHD", "Bahraini Dinar"),
        CurrencyInfo("BIF", "Franc burundais"),
        CurrencyInfo("BMD", "Bermudian Dollar"),
        CurrencyInfo("BND", "Brunei Dollar"),
        CurrencyInfo("BOB", "Boliviano"),
        CurrencyInfo("BRL", "Real brasileiro"),
        CurrencyInfo("BSD", "Bahamian Dollar"),
        CurrencyInfo("BTN", "Bhutanese Ngultrum"),
        CurrencyInfo("BWP", "Botswana Pula"),
        CurrencyInfo("BYN", "Беларускі рубель"),
        CurrencyInfo("BZD", "Belize Dollar"),
        
        // C
        CurrencyInfo("CAD", "Canadian Dollar"),
        CurrencyInfo("CDF", "Franc congolais"),
        CurrencyInfo("CHF", "Schweizer Franken"),
        CurrencyInfo("CLP", "Peso chileno"),
        CurrencyInfo("COP", "Peso colombiano"),
        CurrencyInfo("CRC", "Colón costarricense"),
        CurrencyInfo("CUP", "Peso cubano"),
        CurrencyInfo("CVE", "Escudo cabo-verdiano"),
        CurrencyInfo("CZK", "Česká koruna"),
        
        // D
        CurrencyInfo("DJF", "Franc djiboutien"),
        CurrencyInfo("DKK", "Dansk krone"),
        CurrencyInfo("DOP", "Peso dominicano"),
        CurrencyInfo("DZD", "Dinar algérien"),
        
        // E
        CurrencyInfo("EGP", "Egyptian Pound"),
        CurrencyInfo("ERN", "Eritrean Nakfa"),
        CurrencyInfo("ETB", "Ethiopian Birr"),
        CurrencyInfo("EUR", "Euro"),
        
        // F
        CurrencyInfo("FJD", "Fijian Dollar"),
        CurrencyInfo("FKP", "Falkland Islands Pound"),
        
        // G
        CurrencyInfo("GBP", "Pound Sterling"),
        CurrencyInfo("GEL", "ლარი"),
        CurrencyInfo("GGP", "Guernsey Pound"),
        CurrencyInfo("GHS", "Ghanaian Cedi"),
        CurrencyInfo("GIP", "Gibraltar Pound"),
        CurrencyInfo("GMD", "Gambian Dalasi"),
        CurrencyInfo("GNF", "Franc guinéen"),
        CurrencyInfo("GTQ", "Quetzal guatemalteco"),
        CurrencyInfo("GYD", "Guyanese Dollar"),
        
        // H
        CurrencyInfo("HKD", "Hong Kong Dollar"),
        CurrencyInfo("HNL", "Lempira hondureño"),
        CurrencyInfo("HRK", "Hrvatska kuna"),
        CurrencyInfo("HTG", "Gourde haïtienne"),
        CurrencyInfo("HUF", "Magyar forint"),
        
        // I
        CurrencyInfo("IDR", "Rupiah Indonesia"),
        CurrencyInfo("ILS", "Israeli Shekel"),
        CurrencyInfo("IMP", "Isle of Man Pound"),
        CurrencyInfo("INR", "Indian Rupee"),
        CurrencyInfo("IQD", "Iraqi Dinar"),
        CurrencyInfo("IRR", "Iranian Rial"),
        CurrencyInfo("ISK", "Íslensk króna"),
        
        // J
        CurrencyInfo("JEP", "Jersey Pound"),
        CurrencyInfo("JMD", "Jamaican Dollar"),
        CurrencyInfo("JOD", "Jordanian Dinar"),
        
        // K
        CurrencyInfo("KES", "Kenyan Shilling"),
        CurrencyInfo("KGS", "Kyrgyzstani Som"),
        CurrencyInfo("KHR", "Cambodian Riel"),
        CurrencyInfo("KID", "Kiribati Dollar"),
        CurrencyInfo("KMF", "Franc comorien"),
        CurrencyInfo("KWD", "Kuwaiti Dinar"),
        CurrencyInfo("KYD", "Cayman Islands Dollar"),
        CurrencyInfo("KZT", "Қазақстан теңгесі"),
        
        // L
        CurrencyInfo("LAK", "Lao Kip"),
        CurrencyInfo("LBP", "Lebanese Pound"),
        CurrencyInfo("LKR", "Sri Lankan Rupee"),
        CurrencyInfo("LRD", "Liberian Dollar"),
        CurrencyInfo("LSL", "Lesotho Loti"),
        CurrencyInfo("LYD", "Libyan Dinar"),
        
        // M
        CurrencyInfo("MAD", "Dirham marocain"),
        CurrencyInfo("MDL", "Leu moldovenesc"),
        CurrencyInfo("MGA", "Ariary malgache"),
        CurrencyInfo("MKD", "Македонски денар"),
        CurrencyInfo("MMK", "Myanmar Kyat"),
        CurrencyInfo("MNT", "Mongolian Tögrög"),
        CurrencyInfo("MOP", "Macanese Pataca"),
        CurrencyInfo("MRU", "Ouguiya mauritanien"),
        CurrencyInfo("MUR", "Mauritian Rupee"),
        CurrencyInfo("MVR", "Maldivian Rufiyaa"),
        CurrencyInfo("MWK", "Malawian Kwacha"),
        CurrencyInfo("MXN", "Peso mexicano"),
        CurrencyInfo("MYR", "Malaysian Ringgit"),
        CurrencyInfo("MZN", "Metical moçambicano"),
        
        // N
        CurrencyInfo("NAD", "Namibian Dollar"),
        CurrencyInfo("NGN", "Nigerian Naira"),
        CurrencyInfo("NIO", "Córdoba nicaragüense"),
        CurrencyInfo("NOK", "Norsk krone"),
        CurrencyInfo("NPR", "Nepalese Rupee"),
        CurrencyInfo("NZD", "New Zealand Dollar"),
        
        // O
        CurrencyInfo("OMR", "Omani Rial"),
        
        // P
        CurrencyInfo("PAB", "Balboa panameño"),
        CurrencyInfo("PEN", "Sol peruano"),
        CurrencyInfo("PGK", "Papua New Guinean Kina"),
        CurrencyInfo("PHP", "Philippine Peso"),
        CurrencyInfo("PKR", "Pakistani Rupee"),
        CurrencyInfo("PLN", "Złoty polski"),
        CurrencyInfo("PYG", "Guaraní paraguayo"),
        
        // Q
        CurrencyInfo("QAR", "Qatari Riyal"),
        
        // R
        CurrencyInfo("RON", "Leu românesc"),
        CurrencyInfo("RSD", "Српски динар"),
        CurrencyInfo("RUB", "Российский рубль"),
        CurrencyInfo("RWF", "Franc rwandais"),
        
        // S
        CurrencyInfo("SAR", "Saudi Riyal"),
        CurrencyInfo("SBD", "Solomon Islands Dollar"),
        CurrencyInfo("SCR", "Seychellois Rupee"),
        CurrencyInfo("SDG", "Sudanese Pound"),
        CurrencyInfo("SEK", "Svensk krona"),
        CurrencyInfo("SGD", "Singapore Dollar"),
        CurrencyInfo("SHP", "Saint Helena Pound"),
        CurrencyInfo("SLE", "Sierra Leonean Leone"),
        CurrencyInfo("SLL", "Sierra Leonean Leone (old)"),
        CurrencyInfo("SOS", "Somali Shilling"),
        CurrencyInfo("SRD", "Surinamese Dollar"),
        CurrencyInfo("SSP", "South Sudanese Pound"),
        CurrencyInfo("STN", "Dobra são-tomense"),
        CurrencyInfo("SYP", "Syrian Pound"),
        CurrencyInfo("SZL", "Swazi Lilangeni"),
        
        // T
        CurrencyInfo("THB", "Thai Baht"),
        CurrencyInfo("TJS", "Tajikistani Somoni"),
        CurrencyInfo("TMT", "Turkmenistani Manat"),
        CurrencyInfo("TND", "Dinar tunisien"),
        CurrencyInfo("TOP", "Tongan Paʻanga"),
        CurrencyInfo("TRY", "Türk lirası"),
        CurrencyInfo("TTD", "Trinidad and Tobago Dollar"),
        CurrencyInfo("TVD", "Tuvaluan Dollar"),
        CurrencyInfo("TWD", "New Taiwan Dollar"),
        CurrencyInfo("TZS", "Tanzanian Shilling"),
        
        // U
        CurrencyInfo("UAH", "Українська гривня"),
        CurrencyInfo("UGX", "Ugandan Shilling"),
        CurrencyInfo("USD", "United States Dollar"),
        CurrencyInfo("UYU", "Peso uruguayo"),
        CurrencyInfo("UZS", "Uzbekistani Som"),
        
        // V
        CurrencyInfo("VES", "Bolívar venezolano"),
        CurrencyInfo("VND", "Vietnamese Dong"),
        CurrencyInfo("VUV", "Vanuatu Vatu"),
        
        // W
        CurrencyInfo("WST", "Samoan Tala"),
        
        // X
        CurrencyInfo("XAF", "Franc CFA (BEAC)"),
        CurrencyInfo("XCD", "East Caribbean Dollar"),
        CurrencyInfo("XOF", "Franc CFA (BCEAO)"),
        CurrencyInfo("XPF", "Franc CFP"),
        
        // Y
        CurrencyInfo("YER", "Yemeni Rial"),
        
        // Z
        CurrencyInfo("ZAR", "South African Rand"),
        CurrencyInfo("ZMW", "Zambian Kwacha"),
        CurrencyInfo("ZWL", "Zimbabwean Dollar")
    )
    
    /**
     * Get currency info by code.
     */
    fun getCurrencyInfo(code: String): CurrencyInfo? {
        return allCurrencies.find { it.code == code }
    }
    
    /**
     * Get all currency codes (for backward compatibility).
     */
    fun getAllCurrencyCodes(): List<String> {
        return allCurrencies.map { it.code }
    }
}
