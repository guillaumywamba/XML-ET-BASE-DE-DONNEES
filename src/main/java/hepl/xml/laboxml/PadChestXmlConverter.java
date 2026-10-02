package hepl.xml.laboxml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PadChestXmlConverter {

    // Structures intermédiaires ultra-légères pour optimiser la mémoire RAM
    static class Etude {
        String id;
        String imagesXml = "";
    }

    static class Patient {
        String id;
        String genre;
        String naissance;
        Map<String, Etude> etudes = new HashMap<>();
    }

    public static void main(String[] args) {
        String fichierCsv = "C:/Users/user/Desktop/HEPL BLOC3/XML/LABO/PADCHEST_chest_x_ray_images_labels_160K_01.02.19.csv"; // À adapter selon votre fichier
        String fichierXml = "PadChest.xml";

        // Table de hachage principale : Clé = PatientID
        Map<String, Patient> basePatients = new HashMap<>();

        System.out.println("[Labo] Étape 1 : Lecture du CSV et structuration en mémoire...");

        try (BufferedReader br = new BufferedReader(new FileReader(fichierCsv))) {
            String ligneEntete = br.readLine();
            if (ligneEntete == null) {
                System.out.println("Fichier CSV vide.");
                return;
            }

            // Cartographie dynamique des index pour éviter les décalages de colonnes
            String[] headers = ligneEntete.split(",");
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                idx.put(headers[i].trim(), i);
            }

            int c;
            StringBuilder accum = new StringBuilder();
            List<String> colonnesLigne = new ArrayList<>();
            boolean dansGuillemets = false;

            // Lecture caractère par caractère : résiste aux sauts de ligne internes du champ 'Report'
            while ((c = br.read()) != -1) {
                char ch = (char) c;

                if (ch == '"') {
                    dansGuillemets = !dansGuillemets;
                    continue;
                }

                if (ch == ',' && !dansGuillemets) {
                    colonnesLigne.add(accum.toString().trim());
                    accum.setLength(0);
                    continue;
                }

                if ((ch == '\n' || ch == '\r') && !dansGuillemets) {
                    if (ch == '\r') continue; // Ignore le retour chariot Windows
                    colonnesLigne.add(accum.toString().trim());
                    accum.setLength(0);

                    // Traitement de la ligne si elle est complète et valide
                    if (colonnesLigne.size() > 0 && !colonnesLigne.get(0).isEmpty() && idx.containsKey("PatientID")) {
                        Integer pIdx = idx.get("PatientID");
                        if (pIdx != null && pIdx < colonnesLigne.size() && !colonnesLigne.get(pIdx).isEmpty()) {
                            traiterLigneCsv(colonnesLigne, basePatients, idx);
                        }
                    }
                    colonnesLigne.clear();
                    continue;
                }
                accum.append(ch);
            }
        } catch (Exception e) {
            System.err.println("Erreur de parsing : " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[Labo] Étape 2 : Écriture du fichier XML hiérarchique...");

        // Écriture du flux XML final avec injection de la DTD pour validation
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fichierXml))) {
            bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            bw.write("<!DOCTYPE PadChest [\n");
            bw.write("  <!ELEMENT PadChest (Patient*)>\n");
            bw.write("  <!ELEMENT Patient (Etude*)>\n");
            bw.write("  <!ATTLIST Patient id ID #REQUIRED genre CDATA #IMPLIED naissance CDATA #IMPLIED>\n");
            bw.write("  <!ELEMENT Etude (Image*)>\n");
            bw.write("  <!ATTLIST Etude id CDATA #REQUIRED>\n");
            bw.write("  <!ELEMENT Image (ImageDir, Projection, Pediatric, MethodProjection, Rapport, Diagnostique)>\n");
            bw.write("  <!ATTLIST Image id CDATA #REQUIRED>\n");
            bw.write("  <!ELEMENT ImageDir (#PCDATA)>\n");
            bw.write("  <!ELEMENT Projection (#PCDATA)>\n");
            bw.write("  <!ELEMENT Pediatric (#PCDATA)>\n");
            bw.write("  <!ELEMENT MethodProjection (#PCDATA)>\n");
            bw.write("  <!ELEMENT Rapport (Report)>\n");
            bw.write("  <!ATTLIST Rapport id ID #REQUIRED MethodLabel CDATA #IMPLIED>\n");
            bw.write("  <!ELEMENT Report (#PCDATA)>\n");
            bw.write("  <!ELEMENT Diagnostique (Labels, Localizations, LabelsLocalizationsBySentence, LabelCUIS, LocalizationsCUIS)>\n");
            bw.write("  <!ELEMENT Labels (#PCDATA)>\n");
            bw.write("  <!ELEMENT Localizations (#PCDATA)>\n");
            bw.write("  <!ELEMENT LabelsLocalizationsBySentence (#PCDATA)>\n");
            bw.write("  <!ELEMENT LabelCUIS (#PCDATA)>\n");
            bw.write("  <!ELEMENT LocalizationsCUIS (#PCDATA)>\n");
            bw.write("]>\n");
            bw.write("<PadChest>\n");

            // Génération de l'arborescence textuelle sans duplication des données Patients / Études
            for (Patient p : basePatients.values()) {
                bw.write(String.format("  <Patient id=\"P_%s\" genre=\"%s\" naissance=\"%s\">\n",
                        p.id, echapperXml(p.genre), p.naissance.replace(".0", ""))); // Nettoyage du format double (1930.0 -> 1930)

                for (Etude e : p.etudes.values()) {
                    bw.write(String.format("    <Etude id=\"%s\">\n", e.id));
                    bw.write(e.imagesXml);
                    bw.write("    </Etude>\n");
                }
                bw.write("  </Patient>\n");
            }
            bw.write("</PadChest>\n");
            System.out.println("Conversion accomplie avec succès !");
        } catch (Exception e) {
            System.err.println("Erreur d'écriture XML : " + e.getMessage());
        }
    }

    private static void traiterLigneCsv(List<String> cols, Map<String, Patient> basePatients, Map<String, Integer> idx) {
        // Extraction sécurisée des valeurs
        String pId = getVal(cols, idx, "PatientID");
        String sId = getVal(cols, idx, "StudyID");
        String imgId = getVal(cols, idx, "ImageID");

        // Niveau 1 : Regroupement par Patient
        Patient patient = basePatients.computeIfAbsent(pId, k -> {
            Patient p = new Patient();
            p.id = pId;
            p.genre = getVal(cols, idx, "PatientSex_DICOM");
            p.naissance = getVal(cols, idx, "PatientBirth");
            return p;
        });

        // Niveau 2 : Regroupement par Étude sous le Patient
        Etude etude = patient.etudes.computeIfAbsent(sId, k -> {
            Etude e = new Etude();
            e.id = sId;
            return e;
        });

        // Niveau 3 : Construction de l'élément Image complexe avec ses structures imbriquées
        StringBuilder img = new StringBuilder();
        img.append(String.format("      <Image id=\"%s\">\n", imgId));
        img.append(String.format("        <ImageDir>%s</ImageDir>\n", echapperXml(getVal(cols, idx, "ImageDir"))));
        img.append(String.format("        <Projection>%s</Projection>\n", echapperXml(getVal(cols, idx, "Projection"))));
        img.append(String.format("        <Pediatric>%s</Pediatric>\n", echapperXml(getVal(cols, idx, "Pediatric"))));
        img.append(String.format("        <MethodProjection>%s</MethodProjection>\n", echapperXml(getVal(cols, idx, "MethodProjection"))));

        // Imbrication sémantique du Rapport (Contraint ID de référence interne)
        String rId = getVal(cols, idx, "ReportID");
        img.append(String.format("        <Rapport id=\"R_%s\" MethodLabel=\"%s\">\n", rId, echapperXml(getVal(cols, idx, "MethodLabel"))));
        img.append(String.format("          <Report>%s</Report>\n", echapperXml(getVal(cols, idx, "Report"))));
        img.append("        </Rapport>\n");

        // Bloc Diagnostique regroupant les données analytiques et l'UMLS
        img.append("        <Diagnostique>\n");
        img.append(String.format("          <Labels>%s</Labels>\n", echapperXml(getVal(cols, idx, "Labels"))));
        img.append(String.format("          <Localizations>%s</Localizations>\n", echapperXml(getVal(cols, idx, "Localizations"))));
        img.append(String.format("          <LabelsLocalizationsBySentence>%s</LabelsLocalizationsBySentence>\n", echapperXml(getVal(cols, idx, "LabelsLocalizationsBySentence"))));
        img.append(String.format("          <LabelCUIS>%s</LabelCUIS>\n", echapperXml(getVal(cols, idx, "labelCUIS"))));
        img.append(String.format("          <LocalizationsCUIS>%s</LocalizationsCUIS>\n", echapperXml(getVal(cols, idx, "LocalizationsCUIS"))));
        img.append("        </Diagnostique>\n");

        img.append("      </Image>\n");

        // Accumulation sous forme de texte pour économiser les objets Java en RAM
        etude.imagesXml += img.toString();
    }

    private static String getVal(List<String> cols, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i != null && i < cols.size()) {
            return cols.get(i);
        }
        return "";
    }

    // Gestion rigoureuse des caractères spéciaux exigée par l'énoncé
    private static String echapperXml(String texte) {
        if (texte == null || texte.equalsIgnoreCase("nan") || texte.equalsIgnoreCase("None") || texte.isEmpty()) {
            return "";
        }
        return texte.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
