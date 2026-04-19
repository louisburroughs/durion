import re
with open('/home/louis-burroughs/IdeaProjects/durion/docs/journeys/JOURNEY_MAP_SEED_V2.md', 'r') as f:
    text = f.read()

replacements = [
    (r'`#68`, `#79`, `#163`, `#239`', r'`[View, CustomerContext]`, `[Create, DraftEstimate]`, `[Retrieve, Estimate]`'),
    (r'`#67`, `#85`', r'`[Initialize, Intake]`'),
    (r'`#80`, `#84`, `#112`, `#234`, `#235`, `#236`, `#237`, `#238`', r'`[Add, LineItem]`, `[Calculate, Pricing]`, `[Revise, Estimate]`'),
    (r'`#232`, `#233`, `#269`, `#270`, `#271`', r'`[Submit, Estimate]`, `[Approve, Estimate]`'),
    (r'`#268`', r'`[Override, Approval]`, `[Expire, Estimate]`'),
    (r'`#227`, `#228`, `#229`, `#230`, `#231`', r'`[Create, WorkOrder]`, `[Check, Idempotency]`'),
    (r'`#226`', r'`[View, PromotionAudit]`'),
    
    (r'`#74`, `#139`', r'`[Create, Appointment]`'),
    (r'`#76`', r'`[View, Schedule]`'),
    (r'`#131`, `#137`, `#138`', r'`[Update, Schedule]`, `[Cancel, Appointment]`'),
    (r'`#128`, `#133`, `#225`', r'`[Assign, Technician]`, `[Override, Assignment]`'),
    (r'`#225`', r'`[Assign, Technician]`'),
    (r'`#78`', r'`[View, ActiveWork]`'),
    (r'`#77`', r'`[Review, WorkStatus]`'),
    
    (r'`#123`, `#219`', r'`[View, WorkContext]`, `[View, FieldContext]`'),
    (r'`#220`, `#224`', r'`[Start, Work]`, `[Log, AdditionalRequest]`'),
    (r'`#221`, `#222`, `#243`', r'`[Substitute, Part]`, `[Consume, Part]`, `[Return, Part]`'),
    (r'`#93`', r'`[View, InventoryLine]`'),
    (r'`#215`, `#216`, `#217`', r'`[Complete, Work]`, `[Finalize, Billing]`, `[Resolve, Change]`'),
    (r'`#214`', r'`[Reopen, WorkOrder]`'),
    
    (r'`#90`, `#91`, `#97`, `#104`', r'`[Plan, CycleCount]`, `[Approve, CycleCount]`'),
    (r'`#94`, `#95`, `#96`, `#98`, `#99`, `#241`', r'`[Receive, Shipment]`, `[Putaway, Inventory]`'),
    (r'`#92`, `#93`, `#243`, `#244`', r'`[Pick, Parts]`, `[Fulfill, Order]`'),
    (r'`#242`', r'`[Return, Parts]`'),
]

for old, new in replacements:
    text = text.replace(old, new)
    
with open('/home/louis-burroughs/IdeaProjects/durion/docs/journeys/JOURNEY_MAP_SEED_V2.md', 'w') as f:
    f.write(text)
