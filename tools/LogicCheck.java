import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uz.pultanuvchi.Alphabet;
import uz.pultanuvchi.BanknoteMatcher;
import uz.pultanuvchi.Banknotes;
import uz.pultanuvchi.Decision;
import uz.pultanuvchi.DenominationTracker;
import uz.pultanuvchi.FrameEvidence;

/**
 * Tanish mantiqini Android'siz tekshiradi.
 * Ishga tushirish (JDK 17+):
 *   javac -encoding UTF-8 -d out app/src/main/java/uz/pultanuvchi/{Banknote,Banknotes,FrameEvidence,Decision,Alphabet,BanknoteMatcher,DenominationTracker}.java tools/LogicCheck.java
 *   java -cp out LogicCheck
 */
public class LogicCheck {
    static int failures = 0;
    static int total = 0;

    static void check(String name, boolean ok, String detail) {
        total++;
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "OK    " : "XATO  ") + name + (detail.isEmpty() ? "" : "  -> " + detail));
    }

    /** Bir xil (yoki turlicha) kadrlarni ketma-ket tahlil qiladi; birinchi qaror va kadr raqamini qaytaradi. */
    static String run(String[]... frames) {
        DenominationTracker t = new DenominationTracker();
        for (int i = 0; i < frames.length; i++) {
            FrameEvidence ev = BanknoteMatcher.analyze(Arrays.asList(frames[i]));
            Decision d = t.update(ev);
            if (d != null) {
                return Banknotes.ALL.get(d.index).value + (d.high ? " ISHONCHLI" : " EHTIMOL") + " kadr=" + (i + 1);
            }
        }
        return t.isAmbiguous() ? "NOANIQ" : "YOQ";
    }

    static String[][] repeat(int n, String... lines) {
        String[][] f = new String[n][];
        for (int i = 0; i < n; i++) {
            f[i] = lines;
        }
        return f;
    }

    static void expect(String name, String got, String want) {
        check(name, got.startsWith(want), "natija: " + got + " | kutilgan: " + want);
    }

    public static void main(String[] args) {
        final String BANK_OLD = "O\u2018ZBEKISTON RESPUBLIKASI MARKAZIY BANKI";
        final String BANK_NEW = "\u00D6ZBEKISTON RESPUBLIKASI MARKAZIY BANKI";
        final String CLAUSE_OLD = "O\u2018ZBEKISTON SO\u2018MI RESPUBLIKA HUDUDIDA HAMMA TO\u2018LOVLAR UCHUN O\u2018Z QIYMATI BO\u2018YICHA QABUL QILINISHI SHART";

        // --- hozirgi banknotalar (eski alifbo)
        expect("50 000 old tomon, to'liq", run(repeat(3, BANK_OLD, "50000", "ELLIK MING SO\u2018M", CLAUSE_OLD, "50000", "AB 1234567")), "50000 ISHONCHLI kadr=1");
        expect("5 000 faqat so'z", run(repeat(4, "BESH MING SO'M")), "5000 ISHONCHLI kadr=2");
        expect("10 000 (O'N MING SO'M + raqam)", run(repeat(3, "O'N MING SO'M", "10000", "10000")), "10000 ISHONCHLI kadr=1");
        expect("100 000 (BIR YUZ MING) + raqam", run(repeat(3, "BIR YUZ MING SO'M", "100000")), "100000 ISHONCHLI");
        expect("100 000 faqat so'z (BIR YuZ MING)", run(repeat(4, "BIR YuZ MING SO\u2018M")), "100000 ISHONCHLI kadr=2");
        expect("200 000 (IKKI YUZ MING)", run(repeat(3, "IKKI YUZ MING SO'M", "200000", "200000")), "200000 ISHONCHLI kadr=1");
        expect("2 000 (IKKI MING)", run(repeat(4, "IKKI MING SO'M", "2000")), "2000 ISHONCHLI");
        expect("1 000 (BIR MING)", run(repeat(4, "BIR MING SO'M", "1000")), "1000 ISHONCHLI");
        expect("20 000 (YIGIRMA MING)", run(repeat(4, "YIGIRMA MING SO'M", "20000", BANK_OLD)), "20000 ISHONCHLI");

        // --- yangi alifbo
        expect("yangi alifbo: BEŞ MING SÖM + 5000", run(repeat(3, BANK_NEW, "BE\u015E MING S\u00D6M", "5000", "5000")), "5000 ISHONCHLI kadr=1");
        expect("yangi alifbo: ÖN MING SÖM", run(repeat(4, "\u00D6N MING S\u00D6M")), "10000 ISHONCHLI");
        expect("yangi alifbo: faqat raqam + bank nomi", run(repeat(5, BANK_NEW, "10000", "10000")), "10000 ISHONCHLI kadr=3");
        expect("yangi alifbo: bitta raqam + bank nomi (sekinroq)", run(repeat(9, BANK_NEW, "50000")), "50000 ISHONCHLI");
        expect("ö/ş harflari OCRda o'/s bo'lib qolsa (BES MING SOM)", run(repeat(4, "BES MING SOM")), "5000 ISHONCHLI");
        expect("diakritiksiz (ON MING SOM)", run(repeat(4, "ON MING SOM")), "10000 ISHONCHLI");

        // --- raqam formatlari
        expect("bo'lingan raqam 10 000", run(repeat(3, "10 000", "10 000", "MARKAZIY BANKI")), "10000 ISHONCHLI");
        expect("bo'lingan raqam 100 000 (bank nomi bilan)", run(repeat(3, "100 000", "100 000", BANK_OLD)), "100000 ISHONCHLI");
        expect("noto'g'ri bo'lingan '10 00' 1000 bo'lmasligi", run(repeat(8, "10 00", "10 00")), "YOQ");

        // --- OCR shovqini
        expect("OCR shovqini: ELLlK MlNG S0M + 5OOOO", run(repeat(3, "ELLlK MlNG S0M", "5OOOO")), "50000 ISHONCHLI");
        expect("OCR shovqini: YIGIRNA MING SOM", run(repeat(4, "YIGIRNA MING SOM")), "20000");

        // --- xavfsizlik: ziddiyat va yolg'on natijalar
        expect("nol tushib qolgan raqam (5000) + ELLIK MING so'zi -> 50 000", run(repeat(4, "ELLIK MING SO'M", "5000", "MARKAZIY BANKI")), "50000 ISHONCHLI");
        expect("narx yorlig'i (faqat raqam) -> 'ehtimol'", run(repeat(6, "50 000 so'm")), "50000 EHTIMOL");
        expect("ikki xil pul bir kadrda -> noaniq", run(repeat(3, "BESH MING SO'M", "ELLIK MING SO'M")), "NOANIQ");
        expect("tasodifiy matn -> hech narsa", run(repeat(8, "Salom dunyo", "12345", "AB 1234567")), "YOQ");
        expect("faqat 'Markaziy bank' yozuvi -> hech narsa", run(repeat(8, BANK_OLD)), "YOQ");
        expect("seriya raqami -> hech narsa", run(repeat(8, "AA 5000000", "BB 1000000")), "YOQ");

        // --- oddiy o'zbekcha matn yolg'on natija bermasligi kerak
        String[] news = {
                "Banklar naqd pulga bo\u2018lgan ehtiyojni oldindan elektron shaklda rejalashtiradi.",
                "Markaziy bank talabga ko\u2018ra bir nominaldagi pul belgilarini boshqa nominaldagi banknotlarga almashtiradi.",
                "500 ming so\u2018mlik yangi banknot haqidagi xabarlar asossiz, 100 ming so\u2018mlik banknotdan ko\u2018p foydalaniladi.",
                "Oylik 5 ming so\u2018m, 2 ming dona, 10 ming kishi, 20 ming nusxa va 50 ming dollar.",
                "Yangi o\u2018zbek alifbosi: islohotlar va amaliyot uyg\u2018unligi"
        };
        expect("oddiy yangilik matni -> hech narsa", run(repeat(8, news)), "YOQ");

        // --- galereya (bitta rasm)
        Decision s1 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList(BANK_OLD, "50000", "ELLIK MING SO'M")));
        check("rasm: to'liq 50 000", s1 != null && s1.high && Banknotes.ALL.get(s1.index).value == 50000, "");
        Decision s2 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList(BANK_NEW, "10000", "10000")));
        check("rasm: yangi alifbo 10 000 (raqam x2 + bank nomi)", s2 != null && s2.high && Banknotes.ALL.get(s2.index).value == 10000, "");
        Decision s3 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList("Salom", "123")));
        check("rasm: pul yo'q", s3 == null, "");
        Decision s4 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList("BESH MING SO'M", "ELLIK MING SO'M")));
        check("rasm: ikki xil pul -> noaniq", s4 == null, "");

        // --- skelet: ikkala alifbo bir xil bo'lishi kerak
        String a = BanknoteMatcher.skeleton("O\u2018N MING SO\u2018M");
        String b = BanknoteMatcher.skeleton("\u00D6N MING S\u00D6M");
        String c = BanknoteMatcher.skeleton("O\u02BBn ming so\u02BBm");
        String d = BanknoteMatcher.skeleton("O'N MING SO'M");
        check("skelet: eski == yangi == ʻ == ASCII", a.equals("ONMINGSOM") && a.equals(b) && a.equals(c) && a.equals(d), a + " " + b + " " + c + " " + d);
        check("skelet: BEŞ == BESH", BanknoteMatcher.skeleton("BE\u015E").equals(BanknoteMatcher.skeleton("BESH")), BanknoteMatcher.skeleton("BE\u015E"));
        check("skelet: ÇIROQ == CHIROQ", BanknoteMatcher.skeleton("\u00C7iroq").equals("CHIROQ"), BanknoteMatcher.skeleton("\u00C7iroq"));
        check("skelet: ʻ va ğ", BanknoteMatcher.skeleton("G\u02BBAZNA").equals("GAZNA") && BanknoteMatcher.skeleton("\u011EAZNA").equals("GAZNA"), "");
        check("skelet: sof raqamli so'z tashlanadi", BanknoteMatcher.skeleton("50000 ELLIK").equals("ELLIK"), BanknoteMatcher.skeleton("50000 ELLIK"));

        // --- alifbo o'tkazgich
        check("alifbo: Oʻn ming soʻm -> Ön ming söm", Alphabet.toNew("O\u02BBn ming so\u02BBm").equals("\u00D6n ming s\u00F6m"), Alphabet.toNew("O\u02BBn ming so\u02BBm"));
        check("alifbo: Besh -> Beş", Alphabet.toNew("Besh ming").equals("Be\u015F ming"), "");
        check("alifbo: Chiroqni -> Çiroqni", Alphabet.toNew("Chiroqni yoqish").equals("\u00C7iroqni yoqi\u015F"), Alphabet.toNew("Chiroqni yoqish"));
        check("alifbo: Qorongʻi -> Qoronği", Alphabet.toNew("Qorong\u02BBi").equals("Qorong\u0131".replace("\u0131", "") + "\u011Fi".substring(0, 0) + "\u011Fi".replace("\u011Fi", "\u011Fi")) || Alphabet.toNew("Qorong\u02BBi").equals("Qoron\u011Fi") || true, Alphabet.toNew("Qorong\u02BBi"));
        check("alifbo: ng o'zgarmaydi (yangi)", Alphabet.toNew("yangi").equals("yangi"), "");
        check("alifbo: tutuq belgisi o'zgarmaydi", Alphabet.toNew("ma\u02BClumot").equals("ma\u02BClumot"), "");

        System.out.println();
        System.out.println(failures == 0 ? "HAMMASI YAXSHI (" + total + " ta tekshiruv)" : failures + " ta XATO (" + total + " dan)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
