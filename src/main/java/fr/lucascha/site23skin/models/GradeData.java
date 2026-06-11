package fr.lucascha.site23skin.models;

/**
 * Représente la configuration d'un grade :
 * son nom affiché, son département et le fichier PNG de son skin.
 */
public class GradeData {

    private final String id;
    private final String displayName;
    private final String department;
    private final String skinFile;

    public GradeData(String id, String displayName, String department, String skinFile) {
        this.id          = id;
        this.displayName = displayName;
        this.department  = department;
        this.skinFile    = skinFile;
    }

    public String getId()          { return id; }
    public String getDisplayName() { return displayName; }
    public String getDepartment()  { return department; }
    public String getSkinFile()    { return skinFile; }

    @Override
    public String toString() {
        return "GradeData{id='" + id + "', dept='" + department + "', skin='" + skinFile + "'}";
    }
}
