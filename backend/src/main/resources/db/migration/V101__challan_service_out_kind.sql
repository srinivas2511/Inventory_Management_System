-- V101: Service-out challan kind + enforce valid challan_kind values
-- Task 11.8: SERVICE_OUT kind (no stock movement, challan tracking only)

ALTER TABLE ims.delivery_challans DROP CONSTRAINT IF EXISTS delivery_challans_challan_kind_check;

ALTER TABLE ims.delivery_challans ADD CONSTRAINT delivery_challans_challan_kind_check
    CHECK (challan_kind IN ('DELIVERY', 'SERVICE_OUT', 'PURCHASE_RETURN', 'RETURNABLE', 'NON_RETURNABLE'));
