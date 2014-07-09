package brooklyn.management.entitlement;

class AcmeEntitlementManager extends PerUserEntitlementManagerWithDefault {

    public AcmeEntitlementManager() {
        super(Entitlements.root());
        super.addUser("hacker", Entitlements.minimal());
        super.addUser("bob", Entitlements.readOnly());
        super.addUser("alice", Entitlements.root());
    }

}
