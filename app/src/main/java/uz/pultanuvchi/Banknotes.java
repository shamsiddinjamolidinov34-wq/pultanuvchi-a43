package uz.pultanuvchi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Muomaladagi O'zbekiston banknotalari (CBU ma'lumotiga ko'ra):
 * 1 000, 2 000, 5 000, 10 000, 20 000, 50 000, 100 000, 200 000 so'm.
 *
 * Banknotadagi so'z yozuvlari (eski alifboda):
 * BIR MING SO'M, IKKI MING SO'M, BESH MING SO'M, O'N MING SO'M,
 * YIGIRMA MING SO'M, ELLIK MING SO'M, BIR YUZ MING SO'M, IKKI YUZ MING SO'M.
 *
 * Yangi alifboda: O' -> O-umlaut, sh -> s-cedilla, so'm -> so-umlaut-m va hokazo.
 * Skelet shakli har ikkala alifbo uchun bir xil (qarang: BanknoteMatcher.skeleton).
 */
public final class Banknotes {
    private Banknotes() {
    }

    public static final List<Banknote> ALL = Collections.unmodifiableList(Arrays.asList(
            new Banknote(1000, "1 000", "1000", "bir ming so\u02BBm", "BIRMING"),
            new Banknote(2000, "2 000", "2000", "ikki ming so\u02BBm", "IKKIMING"),
            new Banknote(5000, "5 000", "5000", "besh ming so\u02BBm", "BESHMING"),
            new Banknote(10000, "10 000", "10000", "o\u02BBn ming so\u02BBm", "ONMING"),
            new Banknote(20000, "20 000", "20000", "yigirma ming so\u02BBm", "YIGIRMAMING"),
            new Banknote(50000, "50 000", "50000", "ellik ming so\u02BBm", "ELLIKMING"),
            new Banknote(100000, "100 000", "100000", "bir yuz ming so\u02BBm", "BIRYUZMING"),
            new Banknote(200000, "200 000", "200000", "ikki yuz ming so\u02BBm", "IKKIYUZMING")
    ));
}
