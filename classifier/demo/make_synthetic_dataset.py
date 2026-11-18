"""Deterministic SYNTHETIC expense remarks for a prototype demonstration only.

ALGOCOM Task 5: "A synthetic-only dataset demonstrates a prototype and cannot establish accuracy on
real station expenses." Every row is marked synthetic=True. Results from this file must be reported
as SYNTHETIC ONLY and must not be presented as station accuracy. Replace with Owner-reviewed
station remarks (see ../labeling-template.jsonl) for the real benchmark.

Each "subject" below is one group_id: all its phrasings stay in a single split, so the held-out test
measures generalisation to subjects the model never saw rather than to reworded duplicates.
v2: more everyday supply items (tape, markers, cleaning gear) spread over several groups.
Usage: python make_synthetic_dataset.py OUTPUT.jsonl
"""
import json
import sys

SUBJECTS = {
    'Fuel': [
        ('gasolina', ['gasolina para sa motor', 'bumili ng gasolina', 'gasolina 2 litro', 'gas for delivery motorcycle', 'gasoline refill', 'gasolina ng tricycle']),
        ('diesel', ['diesel para sa truck', 'krudo ng delivery truck', 'diesel refill', 'bumili ng diesel', 'diesel 10 liters', 'krudo pang deliver']),
        ('fuel-generic', ['fuel expense', 'fuel for deliveries', 'pang fuel ngayong linggo', 'fuel top up', 'fuel sa petron', 'fuel sa shell station']),
        ('motor-gas', ['full tank ng motor', 'pa full tank delivery motor', 'gas ng motor pang deliver', 'motor fuel', 'petrol for motor', 'unleaded para sa motor']),
        ('genset', ['gasolina ng generator', 'generator fuel', 'krudo ng genset', 'diesel for generator', 'fuel ng generator nung brownout', 'genset refuel']),
        ('lpg', ['lpg para sa delivery van', 'autogas van', 'lpg refill van', 'autolpg ng sasakyan', 'lpg pang byahe', 'van autogas top up']),
        ('premium', ['premium gasoline', 'xcs gasolina', 'premium unleaded', 'blaze gasolina', 'v power gas', 'super premium refill']),
        ('trip-gas', ['gas pauwi galing supplier', 'gasolina papuntang supplier', 'gas sa byahe ng order', 'gas for far deliveries', 'gasolina sa malayong deliver', 'gas sa delivery sa kabilang barangay']),
        ('oil-mix', ['2t oil at gasolina', 'two stroke oil mix', 'gas and oil mix tricycle', 'gasolina at langis', 'langis pang mix', 'oil mix ng tricycle']),
        ('kerosene', ['gaas para sa lampara', 'kerosene', 'bumili ng gaas', 'kerosene refill', 'gaas na pang ilaw', 'gaas 1 litro']),
    ],
    'Electricity': [
        ('meralco', ['meralco bill', 'bayad sa meralco', 'meralco monthly', 'meralco ngayong buwan', 'meralco due', 'meralco payment']),
        ('kuryente', ['bayad kuryente', 'kuryente ng station', 'kuryente bill', 'singil sa kuryente', 'kuryente this month', 'kuryente ng refilling']),
        ('electric-bill', ['electric bill', 'electricity bill', 'power bill', 'electric bill payment', 'monthly electricity', 'electric utility bill']),
        ('coop', ['bayad sa electric cooperative', 'electric coop bill', 'coop kuryente', 'cooperative power bill', 'electric coop payment', 'singil ng coop']),
        ('prepaid-power', ['prepaid kuryente load', 'kuryente load', 'prepaid electricity', 'power load card', 'load ng kuryente', 'electric prepaid top up']),
        ('reconnect', ['reconnection fee meralco', 'reconnection ng kuryente', 'meralco reconnection', 'kuryente reconnect fee', 'power reconnection', 'reconnect fee electric']),
        ('submeter', ['submeter kuryente sa landlord', 'bayad submeter', 'kuryente share sa may ari', 'submeter electric', 'share sa kuryente', 'electric submeter bill']),
        ('arrears', ['utang sa meralco', 'past due electric', 'kuryente arrears', 'late electric bill', 'overdue meralco', 'kuryente na naiwan']),
        ('deposit', ['meralco deposit', 'bill deposit kuryente', 'electric meter deposit', 'deposit sa meralco', 'power deposit', 'kuryente deposit']),
        ('pump-power', ['kuryente ng pump', 'power for ro machine', 'electric consumption ng makina', 'kuryente ng ro', 'power usage ng pump', 'electric use ng machine']),
    ],
    'Water': [
        ('maynilad', ['maynilad bill', 'bayad maynilad', 'maynilad monthly', 'maynilad ngayong buwan', 'maynilad due', 'maynilad payment']),
        ('manila-water', ['manila water bill', 'bayad sa manila water', 'manila water monthly', 'manila water due', 'manila water payment', 'manila water singil']),
        ('tubig', ['bayad tubig', 'tubig bill', 'singil sa tubig', 'tubig ng station', 'tubig this month', 'tubig na source']),
        ('water-district', ['water district bill', 'bayad sa water district', 'gma water district', 'water district payment', 'water district monthly', 'water district singil']),
        ('raw-water', ['raw water supply', 'bumili ng raw water', 'raw water delivery', 'source water purchase', 'raw water tanker', 'raw water hauling']),
        ('water-bill', ['water bill', 'water utility bill', 'monthly water', 'water bill payment', 'water charges', 'water utility']),
        ('tanker', ['tanker ng tubig', 'water tanker delivery', 'tubig galing tanker', 'tanker refill', 'water truck supply', 'tanker na tubig']),
        ('deepwell', ['tubig sa poso', 'deep well water fee', 'bayad poso', 'deepwell water share', 'poso water fee', 'well water fee']),
        ('water-reconnect', ['reconnection tubig', 'water reconnection fee', 'maynilad reconnection', 'tubig reconnect', 'water service reconnect', 'reconnect fee tubig']),
        ('water-meter', ['water meter deposit', 'deposit sa water meter', 'metro ng tubig deposit', 'water meter fee', 'bagong water meter fee', 'water meter installation fee']),
    ],
    'Maintenance': [
        ('pump-repair', ['repair ng pump', 'pinaayos ang pump', 'pump repair', 'sira pump pinagawa', 'pump rewinding', 'ayos ng water pump']),
        ('ro-service', ['ro machine servicing', 'linis ng ro machine', 'service ng ro', 'ro membrane cleaning', 'ro maintenance', 'checkup ng ro system']),
        ('leak', ['ayos ng tagas', 'leak repair', 'tubo na may tagas', 'pinaayos tulo sa tubo', 'fix leaking pipe', 'tagas sa linya']),
        ('motor-repair', ['repair ng delivery motor', 'motor pinagawa', 'change oil ng motor', 'motor tune up', 'gulong ng motor vulcanize', 'ayos ng preno ng motor']),
        ('uv', ['palit uv lamp', 'uv lamp replacement', 'uv sterilizer repair', 'bagong uv bulb', 'uv light ayos', 'uv unit servicing']),
        ('electrical-repair', ['electrician bayad', 'ayos ng wiring', 'electrical repair', 'palit breaker', 'sira saksakan inayos', 'rewiring ng station']),
        ('tank-cleaning', ['linis ng tangke', 'tank cleaning', 'sanitize storage tank', 'linis ng water tank', 'tank flushing', 'cleaning ng reservoir']),
        ('dispenser-repair', ['ayos ng dispenser', 'refill station nozzle repair', 'palit faucet ng dispenser', 'dispenser valve repair', 'nozzle replacement', 'faucet inayos']),
        ('truck-service', ['change oil truck', 'truck tune up', 'pinagawa ang truck', 'truck brake repair', 'truck battery palit', 'vulcanize gulong ng truck']),
        ('labor-repair', ['labor ng technician', 'technician fee', 'service fee ng repairman', 'bayad sa gumawa', 'repairman labor', 'technician visit']),
    ],
    'Supplies': [
        ('caps', ['bumili ng caps', 'gallon caps', 'takip ng gallon', 'caps and seals', 'bote caps', 'takip 500 pcs']),
        ('seals', ['shrink seal', 'plastic seal ng gallon', 'heat shrink caps', 'seal para sa takip', 'shrink wrap seals', 'tamper seals']),
        ('gallons', ['bagong gallon containers', 'bumili ng slim gallon', 'round gallon', 'empty gallon purchase', 'dagdag na gallon', 'gallon na lalagyan']),
        ('filters', ['filter cartridge', 'sediment filter', 'carbon filter', 'palit filter cartridges', 'bumili ng filter', 'micron filter']),
        ('labels', ['stickers ng gallon', 'labels printing', 'brand sticker', 'label na papel', 'sticker ng station', 'printed labels', 'tape at sticker', 'clear tape para sa label', 'label tape', 'marker para sa gallon', 'pentel pen pang label', 'double sided tape']),
        ('cleaning', ['sabon panlinis', 'dishwashing liquid', 'chlorine pang sanitize', 'bleach', 'detergent', 'brush panlinis ng gallon']),
        ('sanitizer', ['alcohol', 'hand sanitizer', 'sanitizer refill', 'isopropyl alcohol', 'alcohol gallon', 'sanitizing solution']),
        ('office', ['ballpen at notebook', 'resibo booklet', 'receipt pad', 'office supplies', 'papel at ballpen', 'logbook', 'scotch tape at gunting', 'masking tape', 'stapler at staples', 'marker pentel pen', 'glue at tape', 'folder at paper clips']),
        ('tissue', ['tissue', 'paper towel', 'basahan', 'trapo', 'tissue rolls', 'pamunas', 'walis at dustpan', 'mop', 'gloves', 'face mask', 'hairnet', 'apron']),
        ('bags', ['plastic bags', 'sando bags', 'garbage bags', 'plastic labo', 'trash bags', 'plastik na supot', 'packing tape', 'tape pang balot', 'straw at tali', 'rubber band', 'packaging tape', 'scotch tape pang selyo']),
    ],
    'Salary': [
        ('sahod', ['sahod ng tauhan', 'sahod ngayong linggo', 'sahod staff', 'sahod ni helper', 'sahod kinsenas', 'sahod katapusan']),
        ('payroll', ['payroll', 'weekly payroll', 'payroll this week', 'staff payroll', 'payroll release', 'payroll cutoff']),
        ('wage', ['daily wage', 'wage ng helper', 'wages for staff', 'arawan na sweldo', 'daily rate ng tauhan', 'wage payout']),
        ('sweldo', ['sweldo ng driver', 'sweldo ng delivery boy', 'sweldo staff', 'sweldo this week', 'sweldo ng cashier', 'sweldo kinsenas']),
        ('vale', ['vale ng staff', 'cash advance ng tauhan', 'cash advance driver', 'vale ni helper', 'advance sa sahod', 'salary advance']),
        ('overtime', ['overtime pay', 'ot ng staff', 'overtime ng driver', 'bayad overtime', 'ot pay this week', 'overtime sa linggo']),
        ('13th', ['13th month pay', 'thirteenth month', '13th month ng staff', 'bonus ng tauhan', 'christmas bonus staff', 'year end bonus']),
        ('relief', ['bayad sa reliever', 'relief staff pay', 'reliever na driver', 'pansamantalang helper bayad', 'reliever wage', 'substitute staff pay']),
        ('commission', ['commission ng delivery', 'komisyon ng driver', 'delivery commission', 'komisyon per gallon', 'sales commission staff', 'commission payout']),
        ('salary', ['salary', 'monthly salary', 'salary ng staff', 'salary release', 'salary for employees', 'salary payout']),
    ],
    'Others': [
        ('permit', ['business permit', 'mayors permit', 'renew permit', 'barangay clearance', 'sanitary permit', 'permit fees']),
        ('lab-test', ['water testing fee', 'bacteriological test', 'lab test ng tubig', 'water analysis', 'potability test', 'water sample test']),
        ('food', ['merienda ng staff', 'pagkain ng tauhan', 'lunch ng staff', 'kape at tinapay', 'snacks', 'ulam ng tauhan']),
        ('load', ['load pang text', 'cellphone load', 'prepaid load', 'globe load', 'smart load', 'mobile data load']),
        ('fare', ['pamasahe', 'jeep fare', 'tricycle fare', 'pamasahe papunta bayan', 'commute fare', 'grab fare']),
        ('rent', ['upa sa pwesto', 'renta ng space', 'rent', 'monthly rent', 'bayad upa', 'renta ngayong buwan']),
        ('donation', ['donasyon sa fiesta', 'ambag sa barangay', 'donation', 'abuloy', 'contribution sa liga', 'ambag sa simbahan']),
        ('internet', ['internet bill', 'wifi bill', 'pldt bill', 'converge bill', 'bayad internet', 'wifi monthly']),
        ('bank', ['bank charges', 'gcash fee', 'transfer fee', 'service charge bangko', 'remittance fee', 'cash out fee']),
        ('misc', ['misc', 'iba pa', 'miscellaneous', 'sari sari', 'other expense', 'iba ibang gastos']),
    ],
}


def language(text):
    filipino = {'ng', 'sa', 'para', 'bumili', 'bayad', 'ayos', 'sahod', 'sweldo', 'tubig', 'kuryente', 'pinagawa', 'pang', 'ngayong', 'buwan', 'linggo', 'tauhan', 'takip', 'gaas', 'krudo', 'upa', 'renta', 'pamasahe', 'ambag', 'iba', 'na', 'at', 'ni'}
    words = set(text.split())
    if words & filipino:
        return 'mixed' if words - filipino else 'fil'
    return 'en'


def main(out):
    rows = []
    for category, subjects in SUBJECTS.items():
        for subject, phrases in subjects:
            for n, phrase in enumerate(phrases, 1):
                rows.append({
                    'id': f'syn-{category.lower()}-{subject}-{n}', 'remarks': phrase, 'category': category,
                    'reviewed': True, 'reviewer_id': 'synthetic-generator-v1',
                    'group_id': f'{category.lower()}:{subject}', 'synthetic': True, 'language': language(phrase),
                })
    with open(out, 'w', encoding='utf-8', newline='\n') as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + '\n')
    print(f'{len(rows)} synthetic rows written to {out}')


if __name__ == '__main__':
    main(sys.argv[1])
