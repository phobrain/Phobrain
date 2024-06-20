package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/** 
 **  KwdUtil - not in use since switching to wordless imagenet usage;
 **  keywords might be brought back in a non-manual or contributed
 **  context, so leaving in place.
 **
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileReader;

public class KwdUtil extends Stdio {

    private static final String[] keepSame = {
            // having that English or accident thing
            "stairs", "steps", "shorts", "xmas", "christmas", 
            "lens", "gas", "penis", "debris", "eucalyptus",
            "eyelashes", "jesus", "tennis", "walrus", "circus",
            "branches", "cactus", "chaps", "trellis", "blinds", 
            "cross", "pants", "mantis", "canvas", "dreadlocks", "initials",
            // -- singular distinguished from plural - 
            //    silly to flatten any plural? TODO
            "clouds", "wire_clouds", "street_clouds", "aerial_clouds", 
            "faces", "knees", "eyes", "arms", "hands", "fingers", 
            "workers", "bushes", "flowers", "tree_flowers", "trees",
            "bars", "stripes", "lights", "flags", 
            "buildings", "windows", "columns", "roofs",
            "shadows", "building_shadows", 
            "towers", "spires", "girders", "cranes", 
            "wires", "sky_wires", "guy_wires",
            "chairs", "cracks", "horns", "boats", 
            "circles", "arrays", "curves", "triangles",
            "reflections", "tracks", 
            "dogs", "cats", "birds",
            "shadow_clocks", "chain_link_slats", 
    };
    private static final Set<String> keepSameSet = new HashSet<>(
                                          Arrays.asList(keepSame));

    public static String flattenPlural(String word) {

        // flatten plurals ending in s unless
        if (!word.endsWith("s")  ||  keepSameSet.contains(word)) {
            return word;
        }
        if (word.endsWith("us")  ||
            word.endsWith("glasses")  ||
            word.endsWith("ss")  ||
            word.endsWith("ous")  ||
            word.endsWith("thes")  ||
            word.endsWith("leaves")  ||
            word.endsWith("ies")) { // panties
 
           return word;

        }
        return word.substring(0, word.length()-1);
    }


    private static final String[] face_types = {
            "face", "faces", "profile", "whiteface", "eyebrow",
            "eyebrows", "tongue", "nose", "lip", "lips", "eye", 
            "eyes", "face_paint", "makeup", "eyelashes", "teeth",
            "beard", "skull", "kiss", "mask", "smile", 
            "smiles", "mask", "masks", "eat", "eyes_closed", 
            "animal_face", "animal_faces", "mustache", "hold_phone",
            "sing", "michael_jackson",
            "Jerry_Garcia", "Hitchcock", "Frida_Kahlo"
    };
    private static final Set<String> faceSet = new HashSet<>(
                                          Arrays.asList(face_types));

    public static boolean facish(String word) {
        if (word.startsWith("expr_")) {
            return true;
        }
        return faceSet.contains(word);
    }

    private final static Set<String> animal = new HashSet<>();
    private final static Set<String> animals = new HashSet<>();

    {
        String[] l = { "horse", "zebra", "cow", "goat", "pig", "dog",
                       "cat", "lizard", "snake", "alligator", "crocodile",
                       "lion", "leapord", "jaguar", "big_cat", "fox", 
                       "coyote", "wolf", "squirrel", "monkey", "ape", 
                       "baboon", "gorilla", "moose", "llama", "bear", "deer", 
                       "gazelle", "giraffe", "hyena", "rhino", "rat", 
                       "mouse", "armadillo", "walrus", "seal", "elephant", 
                       "dragon", "unicorn", "ride_animal", "stuffed_animal", 
                       "animal_face", "camel", "otter", "elk", "reindeer",
                       "bat", "rabbit"
        };
        for (String w : l) {
            animal.add(w);
            if (!w.endsWith("s")) {
                animals.add(w + "s");
            } else {
                // walruses
                animals.add(w + "es");
            }
        }
    }

    /*
    private final static Set<String> vegetable = new HashSet<>();
    {
        String[] l = { "chard", "tomato", "onion", "eh" };
    }
    */

    private final static Set<String> bird = new HashSet<>();
    private final static Set<String> birds = new HashSet<>();

    {
        String[] l = { "crow", "seagull", "duck", "swan", "owl",
                       "parrot", "eagle", "pelican", "chicken", "pigeon",
                       "robin", "bluejay", "canary", "hawk", "eagle",
                       "bald_eagle", "vulture", "flamingo", "stork"
            };
        for (String w : l) {
            bird.add(w);
            birds.add(w + "s");
        }
    }

    private final static Set<String> flower = new HashSet<>();
    private final static Set<String> flowers = new HashSet<>();

    {
        String[] l = { "rose", "cala_lily" };
        for (String w : l) {
            flower.add(w);
            flowers.add(w + "s");
        }
    }

    private static final String[] warm_types = { // except face_types
            "woman", "man", "women", "men", "girl",
            "boy", "people", "person", "crowd", "baby", 
            "costume", "musician", "child", "children", "vagrant", 
            "couple", "performer", "black_leather", "drag", "nun", "wig",
            "play_music", "dog", "cat", "animal", "hand",
            "hands", "penis", "jesus", "knees", "arm",
            "arms", "leg", "legs", "foot", "feet",
            "worker", "workers", "finger", "fingers", "mannequin", 
            "skin", "lie", "bird", "skeleton", "police", "figure",
            "parade", "pair", "trio", "doll", "kneel", 
            "photographer", "hair", "lean_person", "horse", "dragon", 
            "shorts", "dress", "pose", "stuffed_animal", "rabbit",
            "devil", "tattoo", "dancer", "crouch", "boots", 
            "butt", "bear", "virgin_mary", "reach", "man_on_crane",
            "hold_hands", "embrace", "clown", "cell_phone", 
            "hands_up", "cow", "fur", "bone", "bones", 
            "angel", "arms_open", "red_hair", "ride_animal", "necktie", 
            "street_vendor", "bikini", "barking", "arms_crossed", "wave_hand", 
            "stockings", "pig", "squat", "day_dead", "cowboy", 
            "back_person", "stride", "shirt", "elephant", "breast", 
            "street_performer", "shadow_person", "scarf", "long_hair",
            "santa_claus", "lion", "hand_hip", "family", "elbow",
            "elbows", "deity", "deer", "blonde", "shoulder",
            "shoe", "artist", "witch", "taxidermy",
            "street_fair", "soldier", "shawl", "monkey",
            "head", "hands_pockets", "circle_people", "bra", "tiger",
            "pregnant", "jump", "interview", "dreadlock", "dreadlocks",
            "cartoon", "cook", "body_paint", "actor", "turban",
            "teen", "udder", "row_people", "penguin", "parrot",
            "painter", "giraffe", "goat", "fireman", "duck", 
            "buddha", "belly_dancer", "hat", "big_hat", "helmet", "zebra",
            "vendor", "tie_shoelaces", "sweatshirt", "statue_liberty","snowman",
            "skater", "shaman", "redhead", "party", "mother",
            "mini_skirt", "massage", "leopard", "kitten", "famous_people",
            "barefoot", "apron", "yoga", "street_artist", "squirrel",
            "shake_hands", "seagull", "sailor", "puppet", "priest",
            "pray", "point_finger", "piercings", "pants", "on_phone",
            "old_person", "naked", "coat", "swim", "primate", 
            "mouse", "sock", "socks", "skirt", 
            "juggler", "jeans", "llama", "head_back", "hands_open",
            "handcuffs", "gorilla", "fist", "fairy", 
            "eagle", "dalmation", "bondage", "bat_person",
            "acrobat", "werewolf", "weaver", "underwear", "uniform",
            "traditional_clothes", "superwoman", "spider_man", 
            "spider", "nataraj", "meditation", "hitchcock", "heels",
            "handbag", "purse", "headdress", "drummer", "cowgirl",
            "indian", "frog", "people_street", "bee", "butterfly",
            "in_crowd", "protest", "serpent", "run", "swan",
            "carry", "bald", "dance", "camel", "bat"
    };
    private static final Set<String> warmSet = new HashSet<>(
                                          Arrays.asList(warm_types));

    public static boolean warmish(String word) {
        if (facish(word)) {
            return true;
        }
        if (bird.contains(word)    ||  birds.contains(word)  ||
            animal.contains(word)  ||  animals.contains(word)) {

            return true;
        }

        return warmSet.contains(word);
    }

    public static List<String> munge(String word) {

        String flat = flattenPlural(word);

        List<String> ret = new ArrayList<>();

        // replacements == HACK

        if ("mop".equals(flat)) {
            ret.add("broom");
        } else {
            ret.add(flat);
        }

        // simpler

        if ("seaplane".equals(flat)) {
            ret.add("airplane");
        } else if ("barge".equals(flat)) {
            ret.add("boat");
        } else if ("helmet".equals(flat)) {
            ret.add("hat");
        } else if ("mod_church".equals(flat)) {
            ret.add("church");
            ret.add("curve_surface");
        } else if ("skid_mark".equals(flat)) {
            ret.add("tire_mark");
        } else if ("skid_marks".equals(flat)) {
            ret.add("tire_marks");
        } else if ("brown_paper".equals(flat)) {
            ret.add("paper");
        } else if ("mt_tam".equals(flat)) {
            ret.add("mountain");
        }
        if (ret.size() > 1) {
            return ret;
        }


        // complicated 

        // bridges

        if (!"bridge".equals(flat)  &&  flat.contains("bridge")) {
            ret.add("bridge");
            return ret;
        }

        // foods

        if (flat.startsWith("banana")  ||  flat.startsWith("apple")) {
            ret.add("fruit");
            ret.add("food");
        } else if (flat.startsWith("corn")  ||  
                   flat.startsWith("onion")  ||
                   flat.startsWith("potato")  ||
                   flat.startsWith("cabbage")  ||
                   flat.startsWith("pepper")) {
            ret.add("vegetable");
            ret.add("food");
        } else if (flat.contains("pizza")  ||
                   flat.contains("barbeque")  ||
                   flat.contains("empanada")  ||
                   flat.contains("shishkabob")  ||
                   flat.contains("cupcake")  ||
                   flat.contains("pastry")  ||
                   flat.contains("pastries")  ||
                   flat.contains("wrapped_food")  ||
                   flat.contains("tofu")) {
            ret.add("prepared_food");
            ret.add("food");
        } else if (flat.contains("fruit")  ||  
                   flat.contains("vegetable") ||
                   flat.contains("rice") ||
                   flat.contains("meat") ||
                   flat.equals("produce")) {
            ret.add("food");
        }

        if (!"flower".equals(flat)  &&  !"flowers".equals(flat)) {
            if (flower.contains(flat)) {
                ret.add("flower");
            } else if (flowers.contains(flat)) {
                ret.add("flowers");
            }
        }

        if (!"bird".equals(flat)  &&  !"birds".equals(flat)) {
            if (bird.contains(flat)) {
                ret.add("bird");
            } else if (birds.contains(flat)) {
                ret.add("birds");
            }
        } 

        if (!"animal".equals(flat)  &&  !"animals".equals(flat)) {
            if (animal.contains(flat)) {
                ret.add("animal");
            } else if (animals.contains(flat)) {
                ret.add("animals");
            }
            if (flat.startsWith("leapord")  ||
                flat.startsWith("lion")     ||
                flat.startsWith("jaguar")) {

                ret.add("big_cat");
            } 
        }

        if (ret.size() > 1) {
            return ret;
        }

        // prefixes

        if (flat.startsWith("expr_")) {
            ret.add("expression");
        } else if (flat.startsWith("letter_")) {
            ret.add("alpha_letter");
        } else if (flat.startsWith("lownum_")) {
            // nums < 10
            ret.add("lownum");
            ret.add("number");
        } else if ("house_number".equals(flat)) {
            ret.add("number");
        } else if ("ball".equals(flat)) {
            ret.add("sphere");
        } else if ("shroud".equals(flat)) {
            ret.add("wrapped");
        } else if ("traffic_cone".equals(flat)) {
            ret.add("orange");
        } else if ("stop_sign".equals(flat)) {
            ret.add("sign");
        } else if ("no_parking".equals(flat)) {
            ret.add("prohibit");
            ret.add("sign");
        } else if ("exit".equals(flat)) {
            ret.add("sign");
        } else if ("drag".equals(flat)) {
            ret.add("costume");
        } else if ("plywood".equals(flat)) {
            ret.add("wood");
        } else if ("stride".equals(flat)) {
            ret.add("walk");
        } else if ("merry_go_round".equals(flat)) {
            ret.add("amusement_park");
        } else if ("worker_on_crane".equals(flat)) {
            ret.add("worker");
        } else if (flat.endsWith("_tower")) {
            ret.add("tower");
        } 

        return ret;
    }

    public static String stripGeom(String flat) { // already munged etc

        // leaving in 'vanishing' for now
        // 'blackwhite' semi fits :-)

        if ((flat.startsWith("juxtapose")  &&  
             !flat.equals("juxtapose_color")) ||
            flat.startsWith("angular")  ||
            flat.startsWith("cross")  ||
            flat.startsWith("circle")  ||
            flat.startsWith("flat_circle")  ||
            flat.startsWith("array")  ||
            flat.startsWith("curve")  ||
            flat.contains("geom")     ||
            "round".equals(flat)      || "sinuous".equals(flat)     ||  
            "square".equals(flat)     ||  "parallel".equals(flat)   ||
            "radiate".equals(flat)    ||  "texture".equals(flat)    ||  
            "mesh".equals(flat)       || "matrix".equals(flat)      ||  
            "triangle".equals(flat)   || "triangles".equals(flat)   ||
            "zig_zag".equals(flat)    ||  "volume".equals(flat)     ||
            "pattern".equals(flat)    ||  "spiral".equals(flat)     ||
            "striated".equals(flat)   ||  "array".equals(flat)      ||
            "image".equals(flat)      || "offset".equals(flat)) {

            return null;
        }
        return flat;
    }

    public static double kwdScale(int method, int count) {
        return 1.0 / Math.pow((double) count, (double) method);
    }

    public static List<String> mungeEigenWords(List<String> kwds) {

        List<String> k2 = new ArrayList<>();
        for (String kwd : kwds) {
            if ("belladonna".equals(kwd)) {
                continue;
            }
            kwd = kwd.replace("wrought_iron", "wrought-iron")
                     .replace("fire_escape", "escape_ladder")
                     .replace("barbed_wire", "barbed-wire")
                     .replace("barbed_fence", "barbed-wire_fence");
            if ("icecream".equals(kwd)) {
                k2.add("ice-cream");
            } else if ("city_scape".equals(kwd)  ||  
                       "street_scape".equals(kwd)) {
                k2.add(kwd.replace("_", ""));
            } else if ("barber_shop".equals(kwd)) {
                k2.add("barbershops");
            } else if ("pinecone".equals(kwd)) {
                k2.add("pine_cone");
            } else if ("guy_wire".equals(kwd)) {
                k2.add("cable");
            } else if ("microta".equals(kwd)) {
                k2.add("small");
            } else if ("postbox".equals(kwd)) {
                k2.add("mailbox");
            } else if ("saltshaker".equals(kwd)) {
                k2.add("salt_shaker");
            } else if ("aquaduct".equals(kwd)) {
                k2.add("aqueduct");
            } else if ("nataraj".equals(kwd)) {
                k2.add("shiva");
            } else if ("rubberband".equals(kwd)) {
                k2.add("elastic_band");
            } else if ("tictactoe".equals(kwd)) {
                k2.add("tic-tac-toe");
            } else if ("juxtapose_ontop".equals(kwd)) {
                k2.add("superimpose");
            } else if ("polkadot".equals(kwd)) {
                k2.add("polka-dot");
            } else if ("blackwhite".equals(kwd)) {
                k2.add("black_white");
            } else if ("overpaint".equals(kwd)) {
                k2.add("repainted");
            } else if ("sunset2".equals(kwd)) {
                k2.add("sunset_dusky");
            } else if ("fishtank".equals(kwd)) {
                k2.add("fishbowl");
            } else if ("occluded".equals(kwd)) {
                k2.add("hidden");
            } else if ("tshirt".equals(kwd)) {
                k2.add("tee-shirt");
            } else if ("cofc".equals(kwd)) {
                k2.add("modern_church");
            } else if ("transam".equals(kwd)) {
                k2.add("pyramid-shaped");
            } else if ("sawhorse".equals(kwd)) {
                k2.add("sawhorses"); // ??
            } else if ("abraded".equals(kwd)) {
                k2.add("sanded"); // ??
            } else if ("mudra".equals(kwd)) {
                k2.add("gesture");
            } else if ("pauvre".equals(kwd)) {
                k2.add("pitiful");
            } else if ("tipi".equals(kwd)) {
                k2.add("teepee");
            } else if ("couvercle".equals(kwd)) {
                k2.add("lid");
            } else if ("wainscot".equals(kwd)) {
                k2.add("sheathing");
            } else if ("touchup".equals(kwd)) {
                k2.add("touch-up");
            } else if ("shishkabob".equals(kwd)) {
                k2.add("kabobs");
            } else if ("tel_pole".equals(kwd)) {
                k2.add("utility_pole");
            } else if ("pixilate".equals(kwd)) {
                k2.add("pixelated");
            } else if ("bandolier".equals(kwd)) {
                k2.add("bandoliers");
            } else if ("NelsonMandela".equals(kwd)) {
                k2.add("Mandela");
            } else if ("lownum".equals(kwd)) {
            } else if ("lownum".equals(kwd)) {
                // skip
            } else if (kwd.startsWith("lownum_")) {
                switch (kwd.charAt("lownum_".length())) {
                    case '0':
                        k2.add("number-zero"); break;
                    case '1':
                        k2.add("number-one"); break;
                    case '2':
                        k2.add("number-two"); break;
                    case '3':
                        k2.add("number-three"); break;
                    case '4':
                    case '5':
                        k2.add("few"); break;
                    default:
                        err("Ya call-a dis 'lownum_'?? " + kwd);               
                }
            } else {
                k2.add(kwd);
            }
        }
        return k2;
    }

    private static int l1, l2, l3;

    public static Map<String, double[]> makeVectorMap(String fname, 
                                                      List<String> kwds)
            throws Exception {

        loadVectorFile(fname);

        pout("vector file: " + fname +
             " vectors: " + vectorMap.size());

        // munge for vector lexicon
        
        l1 = 0; l2 = 0; l3 = 0;
        Map<String, double[]> ret = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String kwd : kwds) {
            double[] v = myMapVec(kwd);
            if (v == null) {
                missing.add(kwd);
            } else {
                ret.put(kwd, myMapVec(kwd));
            }
        }
        pout("L's: " + l1 + " " + l2 + " " + l3);
        if (missing.size() > 0) {
            pout("Missing: " + missing.toString());
            System.exit(1);
        }

        return ret;
    }


    private static double[] myMapVec(String kwd) throws Exception {
        double[] ret = vectorMap.get(kwd);
        if (ret != null) {
            l1++;
            return ret;
        }
        if (kwd.contains("_")) {
            ret = new double[vectorLen];
            String[] wds = kwd.split("_");
            for (String wd : wds) {
                if ("expr".equals(wd)) {
                    wd = "expression";
                } else if ("obj".equals(wd)) {
                    wd = "object";
                } else if ("geom".equals(wd)) {
                    wd = "geometric";
                } else if ("shadow2".equals(wd)) {
                    wd = "shadow";
                } else if ("occluded".equals(wd)) {
                    wd = "hidden";
                } else if ("spikey".equals(wd)) {
                    wd = "spiked";
                }
                double[] v = vectorMap.get(wd);
                if (v == null) {
                    // upper 1st letter
                    v = vectorMap.get(Character.toUpperCase(wd.charAt(0)) + 
                                      wd.substring(1));
                } 
                if (v == null) {
                    v = vectorMap.get(wd.toUpperCase());
                } 
                if (v == null) {
                    pout("No vec for: " + kwd + "/" + wd);
                    return null;
                }
                ret = addVec(ret, v);
            }
            return ret;
        }
        ret = vectorMap.get(Character.toUpperCase(kwd.charAt(0)) + 
                            kwd.substring(1));
        if (ret != null) {
            l2++;
            return ret;
        }
        ret = vectorMap.get(kwd.toUpperCase());
        if (ret != null) {
            l3++;
            return ret;
        }
        return null;
        //throw new RuntimeException("No vec for: " + kwd);
    }

    private static double[] addVec(double[] d1, double[] d2) {
        if (d1.length != d2.length) {
            throw new RuntimeException("Vec len mismatch: " + 
                                       d1.length + "/" + d2.length);
        }
        double[] ret = new double[d1.length];
        for (int i=0; i<ret.length; i++) {
            ret[i] = d1[i] + d2[i];
        }
        return ret;
    }

    private static Map<String, double[]> vectorMap;
    private static int vectorLen = 0;

    private static void loadVectorFile(String fname) throws Exception {

        vectorMap = new HashMap<>();

        File f = new File(fname);

        if (!f.isFile()) {
            throw new RuntimeException("loadVectorFile("+fname+"): not a file");
        }

        pout("Loading kwd vecs: " + fname);

        BufferedReader in = null;
        int lineN = 0;
        String line = "start";
        vectorLen = -1;
        double[] vec = { 0.0 };;
        try {
            in = new BufferedReader(new FileReader(f));
            while ((line = in.readLine()) != null) {
                lineN++;
                if (lineN % 1000 == 0) {
                    dot();
                }
                if (lineN % 70000 == 0) {
                    println();
                }

                //if (line.startsWith("--")) {
                //    continue;
                //}
                String ss[] = line.split(" ");
                if (vectorLen == -1) {
                    vectorLen = ss.length - 1;
                    vec = new double[vectorLen];
                } else if (ss.length != vectorLen+1) {
                    throw new RuntimeException("loadVectorFile(" + fname +
                                               ", line " + lineN +
                                               "): expected len " + vectorLen +
                                               " got " + ss.length);
                }
                for (int i=0; i<vectorLen; i++) {
                    vec[i] = Double.parseDouble(ss[i+1]);
                }
                vectorMap.put(ss[0], vec);
            }
            println();
        } catch (Exception e) {
            throw new RuntimeException("loadVectorFile(" + fname + "): " +
                                       " line " + lineN + "\n" +
                                       line, e);
        } finally {
            try { in.close(); } catch (Exception ignore) {}
        }

    }
}
