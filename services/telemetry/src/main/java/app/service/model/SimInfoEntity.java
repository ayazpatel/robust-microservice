package app.service.model;

public class SimInfoEntity {

    private String simSlot;
    private String simOperator;
    private String simState;
    private String phoneNumber;
    private String countryIso;

    public SimInfoEntity() {
        // no-op
    }

    public String getSimSlot() { return simSlot; }
    public void setSimSlot(String simSlot) { this.simSlot = simSlot; }

    public String getSimOperator() { return simOperator; }
    public void setSimOperator(String simOperator) { this.simOperator = simOperator; }

    public String getSimState() { return simState; }
    public void setSimState(String simState) { this.simState = simState; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getCountryIso() { return countryIso; }
    public void setCountryIso(String countryIso) { this.countryIso = countryIso; }
}